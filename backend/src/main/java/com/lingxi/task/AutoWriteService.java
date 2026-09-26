package com.lingxi.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.ai.AiService;
import com.lingxi.chapter.ChapterService;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.common.BizException;
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
import reactor.core.publisher.Flux;

import java.time.Duration;
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
    private final ObjectMapper objectMapper;

    /** 全局串行执行池（2 线程）：任务在池内排队执行，天然避免同一本书并发写冲突 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "auto-write");
        t.setDaemon(true);
        return t;
    });

    public AutoWriteService(WritingTaskRepository taskRepository, NovelService novelService,
                            ChapterService chapterService, AiService aiService, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.novelService = novelService;
        this.chapterService = chapterService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 发起自动创作任务（校验意图参数；每本书同时只允许一个进行中任务） */
    public TaskVO start(Long novelId, User user, AutoWriteReq req) {
        novelService.loadOwned(novelId, user);
        int chapters = req.chapters() == null ? 5 : req.chapters();
        int wordsPerChapter = req.wordsPerChapter() == null ? 1200 : req.wordsPerChapter();
        String mode = req.mode() == null ? "AUTO" : req.mode();
        if (chapters < 1 || chapters > 50) {
            throw new BizException(400, "章节数需在 1-50 之间", 400);
        }
        if (wordsPerChapter < 300 || wordsPerChapter > 5000) {
            throw new BizException(400, "每章字数需在 300-5000 之间", 400);
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
        task.setCurrentChapter(0);
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
            taskRepository.save(task);

            Novel novel = novelService.loadOwned(task.getNovelId(), user);
            Params p = readParams(task.getParams());

            // 步骤 1：可选——生成全书大纲（仅在作品还没有大纲时）
            if (p.genOutline && (novel.getOutline() == null || novel.getOutline().isBlank())) {
                String outline = collect(aiService.outline(novel.getId(), user));
                if (!outline.isBlank()) {
                    novelService.update(novel.getId(), user,
                            new UpdateReq(null, null, null, null, null, null, outline));
                }
            }

            // 步骤 2：逐章生成
            while (task.getCurrentChapter() < task.getTotalChapters() && isActive(task.getId())) {
                int next = task.getCurrentChapter() + 1;
                var ch = chapterService.create(novel.getId(), user, new SaveReq(null, null, null));
                String content = collect(aiService.writeChapter(novel.getId(), ch.id(), p.wordsPerChapter, user));
                if (content.isBlank()) {
                    throw new BizException(502, "AI 未生成正文（第 " + next + " 章）", 502);
                }
                String title = "第" + ch.chapterOrder() + "章";
                chapterService.save(ch.id(), user, new SaveReq(title, content, "ai"));

                task.setCurrentChapter(next);
                task.setCurrentTitle(title);
                task.setError(null);
                taskRepository.save(task);

                if (p.autoSummary) {
                    collect(aiService.summarize(novel.getId(), ch.id(), user));
                }

                // CONFIRM 模式：每完成一章即挂起，等待用户批准
                if ("CONFIRM".equals(task.getMode()) && next < task.getTotalChapters()) {
                    task.setStatus(WritingTaskStatus.WAITING_APPROVAL);
                    taskRepository.save(task);
                    return;
                }
            }
            task.setStatus(task.getCurrentChapter() >= task.getTotalChapters()
                    ? WritingTaskStatus.DONE : WritingTaskStatus.CANCELLED);
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("Auto write task {} failed: {}", taskId, e.toString());
            taskRepository.findById(taskId).ifPresent(t -> {
                t.setStatus(WritingTaskStatus.FAILED);
                t.setError(shortMsg(e));
                taskRepository.save(t);
            });
        }
    }

    /** 每次迭代前重读状态：支持用户取消（CANCELLED 立即停） */
    private boolean isActive(Long taskId) {
        return taskRepository.findById(taskId)
                .map(t -> t.getStatus() == WritingTaskStatus.RUNNING || t.getStatus() == WritingTaskStatus.QUEUED)
                .orElse(false);
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
