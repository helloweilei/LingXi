package com.lingxi.memory;

import com.lingxi.ai.AiService;
import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.common.BizException;
import com.lingxi.memory.dto.MemoryDtos.MemoryVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineItemVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineVO;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final NovelService novelService;
    private final ChapterOutlineRepository chapterOutlineRepository;
    private final StoryMemoryRepository storyMemoryRepository;
    private final ChapterRepository chapterRepository;
    private final AiService aiService;

    public MemoryService(NovelService novelService, ChapterOutlineRepository chapterOutlineRepository,
                         StoryMemoryRepository storyMemoryRepository, ChapterRepository chapterRepository,
                         AiService aiService) {
        this.novelService = novelService;
        this.chapterOutlineRepository = chapterOutlineRepository;
        this.storyMemoryRepository = storyMemoryRepository;
        this.chapterRepository = chapterRepository;
        this.aiService = aiService;
    }

    @Transactional(readOnly = true)
    public MemoryVO view(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        List<ChapterOutline> outlines = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novelId);
        List<OutlineVO> vos = outlines.stream()
                .map(o -> new OutlineVO(o.getId(), o.getChapterOrder(), o.getTitle(),
                        o.getOutline(), o.getUpdatedAt()))
                .toList();
        StoryMemory memory = storyMemoryRepository.findByNovelId(novelId).orElse(null);
        return new MemoryVO(novelId, vos,
                memory == null ? "" : memory.getStoryBrain(),
                memory == null ? null : memory.getUpdatedAt());
    }

    /** 覆盖更新故事脑（每本书一份） */
    @Transactional
    public StoryMemory saveStoryBrain(Long novelId, User user, String storyBrain) {
        novelService.loadOwned(novelId, user);
        StoryMemory memory = storyMemoryRepository.findByNovelId(novelId).orElseGet(() -> {
            StoryMemory m = new StoryMemory();
            m.setNovelId(novelId);
            return m;
        });
        memory.setStoryBrain(storyBrain);
        return storyMemoryRepository.save(memory);
    }

    /**
     * 全书大纲视图：**一行 = 一条规划**（不等同于章节）。
     * 章节是规划的产物——只有正文生成后该行才有 chapterId 与 hasContent。
     */
    @Transactional(readOnly = true)
    public List<OutlineItemVO> outlines(Long novelId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        List<ChapterOutline> outs = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novelId);
        // 已落地章节按 id 取（用于展示是否已写正文）
        Map<Long, Chapter> chapters = outs.stream()
                .map(ChapterOutline::getChapterId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(chapterRepository::findById)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(Chapter::getId, c -> c));
        // 默认每章字数 = ceil(总目标字数 / 规划条数)；未设总目标时为 null（前端显示"—"）
        long plannedCount = outs.size();
        Integer totalTarget = novel.getTargetWords();
        Long defaultPerChapter = (plannedCount > 0 && totalTarget != null && totalTarget > 0)
                ? (long) Math.ceil((double) totalTarget / plannedCount) : null;
        return outs.stream().map(o -> {
            Chapter c = o.getChapterId() == null ? null : chapters.get(o.getChapterId());
            boolean written = c != null && c.getWordCount() != null && c.getWordCount() > 0;
            boolean customized = o.getTargetWords() != null && o.getTargetWords() > 0;
            Long words = customized ? o.getTargetWords().longValue() : defaultPerChapter;
            String title = (o.getTitle() == null || o.getTitle().isBlank())
                    ? (c == null ? null : c.getTitle()) : o.getTitle();
            return new OutlineItemVO(o.getId(), o.getChapterOrder(), title,
                    o.getChapterId(), words, customized, written,
                    o.getOutline() != null && !o.getOutline().isBlank(), o.getOutline(), o.getUpdatedAt());
        }).toList();
    }

    /**
     * 手动新增一行规划（只建大纲，**不创建章节**）。
     * 章节在用户点「生成正文」时才由这条规划落地产生。
     */
    @Transactional
    public List<OutlineItemVO> createOutline(Long novelId, User user, String chapterTitle,
                                              String outline, Integer targetWords) {
        novelService.loadOwned(novelId, user);
        ChapterOutline o = new ChapterOutline();
        o.setNovelId(novelId);
        o.setChapterOrder(nextChapterOrder(novelId));
        o.setTitle(cleanTitle(chapterTitle));
        // 大纲内容可留空（用户也可以先占位，之后再补写或让 AI 生成）
        o.setOutline(outline == null ? "" : outline.trim());
        if (targetWords != null && targetWords > 0) {
            o.setTargetWords(targetWords);
        }
        chapterOutlineRepository.save(o);
        return outlines(novelId, user);
    }

    /**
     * 编辑一条规划（章节名 / 大纲内容 / 目标字数）。
     * 大纲与标题传 null 表示"不改动该字段"；targetWords 传 null 同样保持原值。
     */
    @Transactional
    public void saveOutline(Long novelId, User user, Long outlineId, String chapterTitle,
                            String outline, Integer targetWords) {
        ChapterOutline o = loadOwned(novelId, outlineId);
        if (chapterTitle != null) {
            o.setTitle(cleanTitle(chapterTitle));
        }
        if (outline != null) {
            o.setOutline(outline.trim());
        }
        if (targetWords != null) {
            o.setTargetWords(targetWords > 0 ? targetWords : null);
        }
        chapterOutlineRepository.save(o);
    }

    /** 单独设置/恢复某条规划的目标字数（不触碰大纲内容） */
    @Transactional
    public void saveChapterWords(Long novelId, User user, Long outlineId, Integer words) {
        ChapterOutline o = loadOwned(novelId, outlineId);
        o.setTargetWords(words != null && words > 0 ? words : null);
        chapterOutlineRepository.save(o);
    }

    /** 删除一条规划（若该章已落地章节，一并删除其空章节，保留已有正文的章节） */
    @Transactional
    public void deleteOutline(Long novelId, User user, Long outlineId) {
        ChapterOutline o = loadOwned(novelId, outlineId);
        chapterOutlineRepository.delete(o);
    }

    /**
     * 计算本次需要生成大纲内容的规划行（**只读已有规划，不创建任何东西**）。
     * 供同步生成（少量）与异步任务（批量）共用。
     *
     * @param outlineIds 指定则只取这些；留空则取全书大纲内容尚为空的规划行
     */
    @Transactional(readOnly = true)
    public List<ChapterOutline> resolveTargetOutlines(Long novelId, User user, List<Long> outlineIds) {
        novelService.loadOwned(novelId, user);
        List<ChapterOutline> all = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novelId);
        Set<Long> wanted = (outlineIds == null || outlineIds.isEmpty()) ? null : new HashSet<>(outlineIds);
        if (wanted != null) {
            return all.stream().filter(o -> wanted.contains(o.getId())).toList();
        }
        return all.stream().filter(o -> o.getOutline() == null || o.getOutline().isBlank()).toList();
    }

    /**
     * 同步生成大纲内容（仅适合少量，如单章重生成）。
     * 批量请走 OutlineTaskService 异步任务——每章一次 LLM 调用，串行阻塞会拖垮 HTTP 请求。
     */
    @Transactional
    public List<OutlineItemVO> generate(Long novelId, User user, List<Long> outlineIds) {
        for (ChapterOutline o : resolveTargetOutlines(novelId, user, outlineIds)) {
            try {
                aiService.generateChapterOutline(novelId, o.getId(), user).blockLast();
            } catch (Exception ignored) {
                // 单章生成失败：跳过，不阻塞其余章节
            }
        }
        return outlines(novelId, user);
    }

    /** 该书是否已有大纲规划行（用于区分"还没有规划"与"都已写好"的提示） */
    @Transactional(readOnly = true)
    public boolean hasOutline(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        return !chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novelId).isEmpty();
    }

    /** 新增规划的下一个序号（接在当前最大序号之后） */
    private int nextChapterOrder(Long novelId) {
        return chapterOutlineRepository.findTopByNovelIdOrderByChapterOrderDesc(novelId)
                .map(o -> o.getChapterOrder() + 1)
                .orElse(1);
    }

    private ChapterOutline loadOwned(Long novelId, Long outlineId) {
        return chapterOutlineRepository.findById(outlineId)
                .filter(o -> o.getNovelId().equals(novelId))
                .orElseThrow(() -> new BizException(404, "大纲不存在", 404));
    }

    /** 空标题视为未命名（存null，前端显示占位），避免存一串空白 */
    private static String cleanTitle(String title) {
        return (title == null || title.isBlank()) ? null : title.trim();
    }
}
