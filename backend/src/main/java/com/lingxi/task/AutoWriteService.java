package com.lingxi.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.ai.AiService;
import com.lingxi.chapter.ChapterService;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.common.BizException;
import com.lingxi.memory.ChapterSummaryRepository;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.novel.dto.NovelDtos.UpdateReq;
import com.lingxi.task.dto.WritingTaskDtos.AutoWriteReq;
import com.lingxi.task.dto.WritingTaskDtos.TaskVO;
import com.lingxi.user.User;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动创作流水线：
 * 1. （可选）先生成全书大纲并写入作品；
 * 2. 逐章：建空章节 → AI 生成整章正文 → 落库（reason=ai 快照）→（可选）生成摘要；
 * 3. AUTO 模式一气呵成；CONFIRM 模式每章后进入 WAITING_APPROVAL，等用户批准再继续。
 * 状态机：QUEUED → RUNNING →（CONFIRM）WAITING_APPROVAL → … → DONE / FAILED / CANCELLED
 */
@Service
public class AutoWriteService {

    private static final Logger log = LoggerFactory.getLogger(AutoWriteService.class);
    private static final Duration COLLECT_TIMEOUT = Duration.ofMinutes(15);
    private static final List<WritingTaskStatus> ACTIVE = List.of(
            WritingTaskStatus.QUEUED, WritingTaskStatus.RUNNING, WritingTaskStatus.WAITING_APPROVAL);

    private final WritingTaskRepository taskRepository;
    private final NovelService novelService;
    private final ChapterService chapterService;
    private final AiService aiService;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final ObjectMapper objectMapper;

    /** 全局串行执行池（2 线程）：任务在池内排队执行，天然避免同一本书并发写冲突 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "auto-write");
        t.setDaemon(true);
        return t;
    });

    public AutoWriteService(WritingTaskRepository taskRepository, NovelService novelService,
                            ChapterService chapterService, AiService aiService,
                            ChapterSummaryRepository chapterSummaryRepository, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.novelService = novelService;
        this.chapterService = chapterService;
        this.aiService = aiService;
        this.chapterSummaryRepository = chapterSummaryRepository;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 发起自动创作任务（校验意图参数；每本书同时只允许一个进行中任务）。
     * 支持 startChapter：从第 N 章续跑（跳过前面已生成的章节，用于失败任务断点续写）。 */
    public TaskVO start(Long novelId, User user, AutoWriteReq req) {
        novelService.loadOwned(novelId, user);
        int chapters = req.chapters() == null ? 5 : req.chapters();
        int wordsPerChapter = req.wordsPerChapter() == null ? 1200 : req.wordsPerChapter();
        String mode = req.mode() == null ? "AUTO" : req.mode();
        int startChapter = req.startChapter() == null ? 1 : req.startChapter();
        if (chapters < 1 || chapters > 50) {
            throw new BizException(400, "章节数需在 1-50 之间", 400);
        }
        if (wordsPerChapter < 300 || wordsPerChapter > 5000) {
            throw new BizException(400, "每章字数需在 300-5000 之间", 400);
        }
        if (startChapter < 1 || startChapter > chapters) {
            throw new BizException(400, "起始章节需在 1-" + chapters + " 之间", 400);
        }
        if (taskRepository.existsByNovelIdAndStatusIn(novelId, ACTIVE)) {
            throw new BizException(409, "该书已有进行中的自动创作任务，请先等待或取消", 409);
        }
        Params params = new Params(chapters, wordsPerChapter,
                req.genOutline() == null || req.genOutline(), req.autoSummary() == null || req.autoSummary());
        WritingTask task = new WritingTask();
        task.setNovelId(novelId);
        task.setUserId(user.getId());
        task.setMode(mode);
        task.setStatus(WritingTaskStatus.QUEUED);
        task.setTotalChapters(chapters);
        task.setCurrentChapter(startChapter - 1);
        task.setParams(writeParams(params));
        taskRepository.save(task);
        executor.submit(() -> run(task.getId(), user));
        return toVO(task);
    }

    /** 批准继续（CONFIRM 模式逐章确认） */
    public TaskVO approve(Long taskId, User user) {
        WritingTask task = loadOwned(taskId, user);
        if (task.getStatus() != WritingTaskStatus.WAITING_APPROVAL) {
            throw new BizException(400, "该任务当前不在待确认状态", 400);
        }
        task.setStatus(WritingTaskStatus.RUNNING);
        taskRepository.save(task);
        executor.submit(() -> run(task.getId(), user));
        return toVO(task);
    }

    /** 取消任务（执行器在下一次循环前检查状态并停止） */
    public TaskVO cancel(Long taskId, User user) {
        WritingTask task = loadOwned(taskId, user);
        if (List.of(WritingTaskStatus.DONE, WritingTaskStatus.FAILED, WritingTaskStatus.CANCELLED)
                .contains(task.getStatus())) {
            throw new BizException(400, "该任务已结束，无法取消", 400);
        }
        task.setStatus(WritingTaskStatus.CANCELLED);
        taskRepository.save(task);
        return toVO(task);
    }

    /**
     * 删除任务并移除其生成的内容：
     * 1. 软删本任务创建的章节（正文与版本快照保留，可恢复）；
     * 2. 清理这些章节的 AI 摘要；
     * 3. 若本任务实际生成过全书大纲，则清空大纲（旧大纲在生成时已被覆盖，无法还原）。
     * 仅终态（DONE/FAILED/CANCELLED）任务允许删除。
     */
    @Transactional
    public void remove(Long taskId, User user) {
        WritingTask task = loadOwned(taskId, user);
        if (ACTIVE.contains(task.getStatus())) {
            throw new BizException(400, "任务进行中，请先取消再删除", 400);
        }
        for (Long cid : parseIds(task.getCreatedChapters())) {
            chapterService.delete(cid, user);
            chapterSummaryRepository.deleteByChapterId(cid);
        }
        if (Boolean.TRUE.equals(task.getGeneratedOutline())) {
            Novel novel = novelService.loadOwned(task.getNovelId(), user);
            if (novel.getOutline() != null && !novel.getOutline().isBlank()) {
                novelService.update(novel.getId(), user, new UpdateReq(null, null, null, null, null, null, null, "", null));
            }
        }
        taskRepository.delete(task);
    }

    public TaskVO get(Long taskId, User user) {
        return toVO(loadOwned(taskId, user));
    }

    public List<TaskVO> list(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        return taskRepository.findByNovelIdOrderByIdDesc(novelId).stream()
                .limit(20).map(this::toVO).toList();
    }

    /* ---------------- 执行器 ---------------- */

    private void run(Long taskId, User user) {
        try {
            WritingTask task = taskRepository.findById(taskId).orElseThrow();
            if (task.getStatus() == WritingTaskStatus.CANCELLED) {
                return;
            }
            task.setStatus(WritingTaskStatus.RUNNING);
            saveTask(task);
            if (task.getStatus() != WritingTaskStatus.RUNNING) {
                return; // 启动瞬间用户已取消：保持取消状态
            }

            Novel novel = novelService.loadOwned(task.getNovelId(), user);
            Params p = readParams(task.getParams());

            // 步骤 1：可选——生成全书大纲（仅在作品还没有大纲时）
            if (p.genOutline && (novel.getOutline() == null || novel.getOutline().isBlank())) {
                String outline = collect(aiService.outline(novel.getId(), user));
                if (!outline.isBlank()) {
                    novelService.update(novel.getId(), user,
                            new UpdateReq(null, null, null, null, null, null, null, outline, null));
                    task.setGeneratedOutline(true);
                    saveTask(task);
                }
            }

            // 步骤 2：逐章生成（支持断点续跑：已有内容的章节跳过，失败残留的空壳章节复用填充）
            while (task.getCurrentChapter() < task.getTotalChapters() && isActive(task.getId())) {
                int next = task.getCurrentChapter() + 1;
                var existing = chapterService.findByNovelAndOrder(novel.getId(), next, user);
                Long chapterId;
                int chapterOrder;
                if (existing.isPresent() && existing.get().wordCount() > 0) {
                    // 已有正文：幂等跳过，仅推进进度（续跑场景下前面的章节不动）
                    task.setCurrentChapter(next);
                    task.setCurrentTitle("第" + next + "章");
                    task.setError(null);
                    saveTask(task);
                    continue;
                }
                if (existing.isPresent()) {
                    // 上次失败残留的空壳章节（0 字）：复用，不新建
                    chapterId = existing.get().id();
                    chapterOrder = existing.get().chapterOrder();
                    appendCreatedChapter(task, chapterId);
                    saveTask(task);
                } else {
                    var ch = chapterService.create(novel.getId(), user, new SaveReq(null, null, null));
                    chapterId = ch.id();
                    chapterOrder = ch.chapterOrder();
                    appendCreatedChapter(task, chapterId);
                    saveTask(task); // 先记录章节归属：中途失败也能被删除任务清理
                }
                // 生成整章正文；LLM 偶发空响应时自动重试（共 3 次）
                String content = null;
                for (int attempt = 1; attempt <= 3 && (content == null || content.isBlank()); attempt++) {
                    if (attempt > 1) {
                        log.warn("Auto write novel {} chapter {} returned blank content, retry {}/3",
                                novel.getId(), next, attempt);
                    }
                    content = collect(aiService.writeChapter(novel.getId(), chapterId, p.wordsPerChapter, user));
                }
                if (content.isBlank()) {
                    throw new BizException(502, "AI 未生成正文（第 " + next + " 章）", 502);
                }
                String title = "第" + chapterOrder + "章";
                chapterService.save(chapterId, user, new SaveReq(title, content, "ai"));

                task.setCurrentChapter(next);
                task.setCurrentTitle(title);
                task.setError(null);
                saveTask(task);

                if (p.autoSummary) {
                    collect(aiService.summarize(novel.getId(), chapterId, user));
                }

                // CONFIRM 模式：每完成一章即挂起，等待用户批准
                if ("CONFIRM".equals(task.getMode()) && next < task.getTotalChapters()) {
                    task.setStatus(WritingTaskStatus.WAITING_APPROVAL);
                    saveTask(task);
                    return;
                }
            }
            task.setStatus(task.getCurrentChapter() >= task.getTotalChapters()
                    ? WritingTaskStatus.DONE : WritingTaskStatus.CANCELLED);
            saveTask(task);
        } catch (Exception e) {
            log.error("Auto write task {} failed: {}", taskId, e.toString());
            taskRepository.findById(taskId).ifPresent(t -> {
                if (t.getStatus() == WritingTaskStatus.CANCELLED) {
                    return; // 用户已取消：保持取消状态，不覆盖为失败
                }
                t.setStatus(WritingTaskStatus.FAILED);
                t.setError(shortMsg(e));
                taskRepository.save(t);
            });
        }
    }

    /**
     * 保存任务进度：先重读数据库状态，防止执行器用内存中的旧状态覆盖用户操作。
     * - 用户已取消（CANCELLED）或任务已失败（FAILED）时，保持数据库状态，不回写；
     * - 任务行已被删除时，不再保存（避免把已删除的任务“复活”）。
     */
    private void saveTask(WritingTask task) {
        taskRepository.findById(task.getId()).ifPresent(cur -> {
            task.setStatus(mergeSavedStatus(task.getStatus(), cur.getStatus()));
            taskRepository.save(task);
        });
    }

    /**
     * 合并待保存状态：数据库已是终态（用户取消/任务失败）时，内存中的旧状态不得覆盖。
     */
    static WritingTaskStatus mergeSavedStatus(WritingTaskStatus memory, WritingTaskStatus db) {
        if (db == WritingTaskStatus.CANCELLED || db == WritingTaskStatus.FAILED) {
            return db;
        }
        return memory;
    }

    /** 每次迭代前重读状态：支持用户取消（CANCELLED 立即停） */
    private boolean isActive(Long taskId) {
        return taskRepository.findById(taskId)
                .map(t -> t.getStatus() == WritingTaskStatus.RUNNING || t.getStatus() == WritingTaskStatus.QUEUED)
                .orElse(false);
    }

    private void appendCreatedChapter(WritingTask task, Long chapterId) {
        String cur = task.getCreatedChapters();
        task.setCreatedChapters(cur == null || cur.isBlank() ? String.valueOf(chapterId) : cur + "," + chapterId);
    }

    /** 终态（可删除）判定 */
    static boolean isTerminal(WritingTaskStatus status) {
        return !ACTIVE.contains(status);
    }

    /** 解析逗号分隔的章节 id 列表 */
    static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    private WritingTask loadOwned(Long taskId, User user) {
        return taskRepository.findById(taskId)
                .filter(t -> t.getUserId().equals(user.getId()))
                .orElseThrow(() -> new BizException(404, "任务不存在", 404));
    }

    private String collect(Flux<String> flux) {
        List<String> parts = flux.collectList().block(COLLECT_TIMEOUT);
        return parts == null ? "" : String.join("", parts).trim();
    }

    private String writeParams(Params p) {
        try {
            return objectMapper.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "参数序列化失败", 500);
        }
    }

    private Params readParams(String json) {
        try {
            return objectMapper.readValue(json, Params.class);
        } catch (Exception e) {
            return new Params(5, 1200, false, false);
        }
    }

    private String shortMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : (m.length() > 200 ? m.substring(0, 200) : m);
    }

    private TaskVO toVO(WritingTask t) {
        Params p = readParams(t.getParams());
        return new TaskVO(t.getId(), t.getNovelId(), t.getMode(), t.getStatus().name(),
                t.getTotalChapters(), t.getCurrentChapter(), t.getCurrentTitle(),
                p.chapters, p.wordsPerChapter, p.genOutline, p.autoSummary,
                t.getError(), t.getCreatedAt(), t.getUpdatedAt());
    }

    /** 任务参数（与 DB 中 params JSON 对应） */
    record Params(int chapters, int wordsPerChapter, boolean genOutline, boolean autoSummary) {
    }
}
