package com.lingxi.memory;

import com.lingxi.ai.AiService;
import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.common.BizException;
import com.lingxi.memory.dto.MemoryDtos.GenerateReq;
import com.lingxi.memory.dto.MemoryDtos.MemoryVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineItemVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineReq;
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
        List<ChapterOutline> outlines = chapterOutlineRepository.findByNovelIdOrdered(novelId);
        Map<Long, Chapter> byId = outlines.isEmpty() ? Map.of()
                : chapterRepository.findAllById(outlines.stream().map(ChapterOutline::getChapterId).toList())
                        .stream().collect(Collectors.toMap(Chapter::getId, c -> c));
        List<OutlineVO> vos = outlines.stream()
                .map(o -> {
                    Chapter c = byId.get(o.getChapterId());
                    return new OutlineVO(o.getChapterId(),
                            c == null ? "未知章节" : c.getTitle(),
                            c == null ? null : c.getChapterOrder(),
                            o.getOutline(), o.getUpdatedAt());
                })
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

    /** 全章节大纲视图：每个章节一行，标注是否已生成大纲（含空内容章节，供手动规划） */
    @Transactional(readOnly = true)
    public List<OutlineItemVO> outlines(Long novelId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        List<Chapter> chapters = chapterRepository.findByNovelIdAndDeletedFalseOrderByChapterOrderAscIdAsc(novelId);
        List<ChapterOutline> outs = chapterOutlineRepository.findByNovelIdOrdered(novelId);
        Map<Long, ChapterOutline> byChapter = outs.stream()
                .collect(Collectors.toMap(ChapterOutline::getChapterId, o -> o, (a, b) -> a));
        // 每章计划字数 = ceil(总目标字数 / 章节数)；作品未设置总目标时返回 null（前端显示“—”）
        long chapterCount = chapters.size();
        Integer totalTarget = novel.getTargetWords();
        Long planPerChapter = (chapterCount > 0 && totalTarget != null && totalTarget > 0)
                ? (long) Math.ceil((double) totalTarget / chapterCount) : null;
        return chapters.stream().map(c -> {
            ChapterOutline o = byChapter.get(c.getId());
            boolean written = c.getWordCount() != null && c.getWordCount() > 0;
            return new OutlineItemVO(c.getId(), c.getChapterOrder(), c.getTitle(), planPerChapter, written,
                    o != null, o == null ? null : o.getOutline(), o == null ? null : o.getUpdatedAt());
        }).toList();
    }

    /**
     * 一键/批量生成大纲：指定 chapterIds 则只生成这些章节；留空则生成全书"尚未生成大纲"的章节。
     * 单章失败不影响其余章节。返回更新后的全量列表。
     */
    @Transactional
    public List<OutlineItemVO> generate(Long novelId, User user, List<Long> chapterIds) {
        novelService.loadOwned(novelId, user);
        List<Chapter> chapters = chapterRepository.findByNovelIdAndDeletedFalseOrderByChapterOrderAscIdAsc(novelId);
        Set<Long> wanted = (chapterIds == null || chapterIds.isEmpty()) ? null : new HashSet<>(chapterIds);
        for (Chapter c : chapters) {
            if (wanted != null) {
                if (!wanted.contains(c.getId())) {
                    continue;
                }
            } else if (chapterOutlineRepository.existsByChapterId(c.getId())) {
                continue;
            }
            try {
                aiService.generateChapterOutline(novelId, c.getId(), user).blockLast();
            } catch (Exception ignored) {
                // 单章生成失败：跳过，不阻塞其余章节
            }
        }
        return outlines(novelId, user);
    }

    /** 手动新增 / 覆盖编辑单章大纲（chapter_id 唯一，自动 upsert） */
    @Transactional
    public void saveOutline(Long novelId, User user, Long chapterId, String outline) {
        novelService.loadOwned(novelId, user);
        Chapter chapter = chapterRepository.findById(chapterId)
                .filter(c -> c.getNovelId().equals(novelId) && !c.isDeleted())
                .orElseThrow(() -> new BizException(404, "章节不存在", 404));
        ChapterOutline o = chapterOutlineRepository.findByChapterId(chapterId).orElseGet(() -> {
            ChapterOutline co = new ChapterOutline();
            co.setNovelId(novelId);
            co.setChapterId(chapterId);
            return co;
        });
        o.setOutline(outline);
        chapterOutlineRepository.save(o);
    }

    /** 删除单章大纲 */
    @Transactional
    public void deleteOutline(Long novelId, User user, Long chapterId) {
        novelService.loadOwned(novelId, user);
        chapterOutlineRepository.deleteByChapterId(chapterId);
    }
}
