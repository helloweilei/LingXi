package com.lingxi.memory;

import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.memory.dto.MemoryDtos.MemoryVO;
import com.lingxi.memory.dto.MemoryDtos.SummaryVO;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final NovelService novelService;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final StoryMemoryRepository storyMemoryRepository;
    private final ChapterRepository chapterRepository;

    public MemoryService(NovelService novelService, ChapterSummaryRepository chapterSummaryRepository,
                         StoryMemoryRepository storyMemoryRepository, ChapterRepository chapterRepository) {
        this.novelService = novelService;
        this.chapterSummaryRepository = chapterSummaryRepository;
        this.storyMemoryRepository = storyMemoryRepository;
        this.chapterRepository = chapterRepository;
    }

    @Transactional(readOnly = true)
    public MemoryVO view(Long novelId, User user) {
        novelService.loadOwned(novelId, user);
        List<ChapterSummary> summaries = chapterSummaryRepository.findByNovelIdOrdered(novelId);
        Map<Long, Chapter> byId = summaries.isEmpty() ? Map.of()
                : chapterRepository.findAllById(summaries.stream().map(ChapterSummary::getChapterId).toList())
                        .stream().collect(Collectors.toMap(Chapter::getId, c -> c));
        List<SummaryVO> vos = summaries.stream()
                .map(s -> {
                    Chapter c = byId.get(s.getChapterId());
                    return new SummaryVO(s.getChapterId(),
                            c == null ? "未知章节" : c.getTitle(),
                            c == null ? null : c.getChapterOrder(),
                            s.getSummary(), s.getUpdatedAt());
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
}
