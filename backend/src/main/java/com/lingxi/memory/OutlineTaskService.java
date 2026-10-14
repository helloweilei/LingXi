package com.lingxi.memory;

import com.lingxi.ai.AiService;
import com.lingxi.common.BizException;
import com.lingxi.memory.dto.OutlineTaskDtos.StartReq;
import com.lingxi.memory.dto.OutlineTaskDtos.TaskVO;
import com.lingxi.user.User;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 章节大纲批量生成任务（异步）。
 *
 * 每章大纲是一次独立的 LLM 调用，N 章串行会远超 HTTP 超时阈值，
 * 因此把整批放进后台线程池执行，前端轮询任务进度：
 * QUEUED → RUNNING → DONE / FAILED / CANCELLED
 */
@Service
public class OutlineTaskService {

    private static final Logger log = LoggerFactory.getLogger(OutlineTaskService.class);
    private static final Duration COLLECT_TIMEOUT = Duration.ofMinutes(5);
    private static final List<OutlineTask.Status> ACTIVE = List.of(
            OutlineTask.Status.QUEUED, OutlineTask.Status.RUNNING);

    private final OutlineTaskRepository taskRepository;
    private final MemoryService memoryService;
    private final AiService aiService;

    /** 串行执行池：避免同一本书并发写大纲互相干扰 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "outline-task");
        t.setDaemon(true);
        return t;
    });

    public OutlineTaskService(OutlineTaskRepository taskRepository, MemoryService memoryService, AiService aiService) {
        this.taskRepository = taskRepository;
        this.memoryService = memoryService;
        this.aiService = aiService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 提交批量生成大纲任务，立即返回（不阻塞调用方）。仅对已有规划行生成内容，不创建规划/章节。 */
    @Transactional
    public TaskVO start(Long novelId, User user, StartReq req) {
        // 校验属主（越权返回 404）
        memoryService.outlines(novelId, user);
        List<Long> ids = req == null ? null : req.outlineIds();
        if (memoryService.resolveTargetOutlines(novelId, user, ids).isEmpty()) {
            throw new BizException(400,
                    memoryService.hasOutline(novelId, user)
                            ? "所有大纲都已填写内容，无需重复生成"
                            : "还没有大纲规划，请先点「新增大纲」或「一键生成大纲」创建规划行",
                    400);
        }
        if (taskRepository.existsByNovelIdAndStatusIn(novelId, ACTIVE)) {
            throw new BizException(409, "该书已有进行中的大纲生成任务，请等待完成或先取消", 409);
        }
        OutlineTask task = new OutlineTask();
        task.setNovelId(novelId);
        task.setUserId(user.getId());
        task.setStatus(OutlineTask.Status.QUEUED);
        task.setChapterIds(joinIds(ids));
        taskRepository.save(task);

        Long taskId = task.getId();
        executor.submit(() -> run(taskId, user));
        return toVO(task);
    }

    /** 后台执行：逐条生成大纲内容并推进进度；单条失败只计数、不中断整批 */
    private void run(Long taskId, User actor) {
        try {
            OutlineTask task = load(taskId);
            task.setStatus(OutlineTask.Status.RUNNING);
            taskRepository.save(task);

            List<Long> ids = parseIds(task.getChapterIds());
            List<ChapterOutline> targets = memoryService.resolveTargetOutlines(task.getNovelId(), actor,
                    ids.isEmpty() ? null : ids);
            task.setTotal(targets.size());
            taskRepository.save(task);

            int index = 0;
            for (ChapterOutline o : targets) {
                OutlineTask current = load(taskId);
                if (current.getStatus() == OutlineTask.Status.CANCELLED) {
                    return;
                }
                index++;
                current.setCurrentIndex(index);
                current.setCurrentTitle(o.getTitle());
                taskRepository.save(current);
                try {
                    aiService.generateChapterOutline(task.getNovelId(), o.getId(), actor)
                            .blockLast(COLLECT_TIMEOUT);
                    current.setSucceeded(current.getSucceeded() + 1);
                } catch (Exception e) {
                    log.warn("生成第 {} 章大纲失败: {}", o.getChapterOrder(), e.toString());
                    current.setFailed(current.getFailed() + 1);
                }
                current.setError(null);
                taskRepository.save(current);
            }

            OutlineTask done = load(taskId);
            if (done.getStatus() != OutlineTask.Status.CANCELLED) {
                done.setStatus(OutlineTask.Status.DONE);
                done.setError(null);
                taskRepository.save(done);
            }
        } catch (Exception e) {
            log.error("大纲任务 {} 执行失败", taskId, e);
            try {
                OutlineTask failed = load(taskId);
                failed.setStatus(OutlineTask.Status.FAILED);
                failed.setError(shortMsg(e));
                taskRepository.save(failed);
            } catch (Exception ignored) {
                // 任务记录本身不可用时放弃回写
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TaskVO> list(Long novelId, User user) {
        return taskRepository.findTop20ByNovelIdOrderByIdDesc(novelId).stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .map(OutlineTaskService::toVO).toList();
    }

    @Transactional(readOnly = true)
    public TaskVO get(Long taskId, User user) {
        return toVO(loadOwned(taskId, user));
    }

    /** 取消任务：执行线程会在下一章开始前退出 */
    @Transactional
    public TaskVO cancel(Long taskId, User user) {
        OutlineTask task = loadOwned(taskId, user);
        if (task.isTerminal()) {
            throw new BizException(400, "任务已结束，无法取消", 400);
        }
        task.setStatus(OutlineTask.Status.CANCELLED);
        taskRepository.save(task);
        return toVO(task);
    }

    @Transactional
    public void remove(Long taskId, User user) {
        OutlineTask task = loadOwned(taskId, user);
        if (!task.isTerminal()) {
            throw new BizException(400, "任务进行中，请先取消再删除", 400);
        }
        taskRepository.delete(task);
    }

    private OutlineTask load(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(404, "任务不存在", 404));
    }

    private OutlineTask loadOwned(Long taskId, User user) {
        OutlineTask task = load(taskId);
        if (!task.getUserId().equals(user.getId())) {
            throw new BizException(404, "任务不存在", 404);
        }
        return task;
    }

    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.valueOf(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : (m.length() > 200 ? m.substring(0, 200) : m);
    }

    private static TaskVO toVO(OutlineTask t) {
        return new TaskVO(t.getId(), t.getNovelId(), t.getStatus().name(),
                t.getTotal(), t.getCurrentIndex(), t.getCurrentTitle(),
                t.getSucceeded(), t.getFailed(), t.getError(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}