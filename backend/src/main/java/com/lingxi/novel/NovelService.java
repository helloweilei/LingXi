package com.lingxi.novel;

import com.lingxi.chapter.ChapterRepository;
import com.lingxi.chapter.ChapterVersionRepository;
import com.lingxi.character.CharacterRepository;
import com.lingxi.common.BizException;
import com.lingxi.memory.ChapterSummaryRepository;
import com.lingxi.memory.StoryMemoryRepository;
import com.lingxi.novel.dto.NovelDtos.CreateReq;
import com.lingxi.novel.dto.NovelDtos.DetailVO;
import com.lingxi.novel.dto.NovelDtos.UpdateReq;
import com.lingxi.novel.dto.NovelDtos.VO;
import com.lingxi.user.User;
import com.lingxi.world.WorldSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NovelService {

    private static final String[] COVER_PALETTE = {
            "#6a5acd", "#e8734a", "#2f9e8f", "#d4557f", "#4a7fd4", "#8a6dd6", "#c29240", "#3f9c5f"
    };

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterVersionRepository chapterVersionRepository;
    private final CharacterRepository characterRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final StoryMemoryRepository storyMemoryRepository;

    public NovelService(NovelRepository novelRepository, ChapterRepository chapterRepository,
                        ChapterVersionRepository chapterVersionRepository,
                        CharacterRepository characterRepository, WorldSettingRepository worldSettingRepository,
                        ChapterSummaryRepository chapterSummaryRepository, StoryMemoryRepository storyMemoryRepository) {
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.chapterVersionRepository = chapterVersionRepository;
        this.characterRepository = characterRepository;
        this.worldSettingRepository = worldSettingRepository;
        this.chapterSummaryRepository = chapterSummaryRepository;
        this.storyMemoryRepository = storyMemoryRepository;
    }

    public List<VO> list(User user) {
        List<Novel> novels = novelRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        Map<Long, Long> counts = chapterCounts(novels);
        return novels.stream().map(n -> toVO(n, counts.getOrDefault(n.getId(), 0L))).toList();
    }

    public VO create(User user, CreateReq req) {
        Novel novel = new Novel();
        novel.setUserId(user.getId());
        novel.setTitle(req.title());
        novel.setDescription(orDefault(req.description(), ""));
        novel.setGenre(orDefault(req.genre(), "其他"));
        novel.setStatus("草稿");
        novel.setTargetWords(req.targetWords());
        novel.setOutline("");
        novel.setCoverColor(pickCover(req.coverColor()));
        novelRepository.save(novel);
        return toVO(novel, 0L);
    }

    public DetailVO detail(Long novelId, User user) {
        Novel novel = loadOwned(novelId, user);
        return toDetailVO(novel, chapterRepository.countByNovelIdAndDeletedFalse(novelId));
    }

    @Transactional
    public DetailVO update(Long novelId, User user, UpdateReq req) {
        Novel novel = loadOwned(novelId, user);
        if (req.title() != null && !req.title().isBlank()) {
            novel.setTitle(req.title());
        }
        if (req.description() != null) {
            novel.setDescription(req.description());
        }
        if (req.genre() != null) {
            novel.setGenre(req.genre());
        }
        if (req.status() != null) {
            novel.setStatus(req.status());
        }
        if (req.targetWords() != null) {
            novel.setTargetWords(req.targetWords());
        }
        if (req.coverColor() != null) {
            novel.setCoverColor(req.coverColor());
        }
        if (req.outline() != null) {
            novel.setOutline(req.outline());
        }
        return toDetailVO(novel, chapterRepository.countByNovelIdAndDeletedFalse(novelId));
    }

    @Transactional
    public void delete(Long novelId, User user) {
        Novel novel = loadOwned(novelId, user);
        List<Long> chapterIds = chapterRepository.findIdsByNovelId(novelId);
        chapterRepository.deleteByNovelId(novelId);
        if (!chapterIds.isEmpty()) {
            chapterVersionRepository.deleteByChapterIdIn(chapterIds);
        }
        characterRepository.deleteByNovelId(novelId);
        worldSettingRepository.deleteByNovelId(novelId);
        chapterSummaryRepository.deleteByNovelId(novelId);
        storyMemoryRepository.deleteByNovelId(novelId);
        novelRepository.delete(novel);
    }

    /** 校验属主后返回实体，供章节/设定/AI 模块复用 */
    public Novel loadOwned(Long novelId, User user) {
        return novelRepository.findByIdAndUserId(novelId, user.getId())
                .orElseThrow(() -> new BizException(404, "作品不存在", 404));
    }

    private Map<Long, Long> chapterCounts(List<Novel> novels) {
        Map<Long, Long> counts = new HashMap<>();
        if (novels.isEmpty()) {
            return counts;
        }
        for (Object[] row : chapterRepository.countGroupedByNovelId(novels.stream().map(Novel::getId).toList())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private String pickCover(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        int idx = (int) (System.nanoTime() % COVER_PALETTE.length);
        return COVER_PALETTE[idx];
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static VO toVO(Novel n, Long chapterCount) {
        return new VO(n.getId(), n.getTitle(), n.getDescription(), n.getGenre(), n.getStatus(),
                n.getTargetWords(), n.getWordCount(), n.getCoverColor(), chapterCount,
                n.getCreatedAt(), n.getUpdatedAt());
    }

    private static DetailVO toDetailVO(Novel n, Long chapterCount) {
        return new DetailVO(n.getId(), n.getTitle(), n.getDescription(), n.getGenre(), n.getStatus(),
                n.getTargetWords(), n.getWordCount(), n.getCoverColor(), n.getOutline(),
                chapterCount, n.getCreatedAt(), n.getUpdatedAt());
    }
}
