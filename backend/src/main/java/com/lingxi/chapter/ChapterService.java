package com.lingxi.chapter;

import com.lingxi.chapter.dto.ChapterDtos.MetaVO;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.chapter.dto.ChapterDtos.VO;
import com.lingxi.common.BizException;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final NovelService novelService;

    public ChapterService(ChapterRepository chapterRepository, NovelService novelService) {
        this.chapterRepository = chapterRepository;
        this.novelService = novelService;
    }

    @Transactional(readOnly = true)
    public List<MetaVO> list(Long novelId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        return chapterRepository.findByNovelIdOrderByChapterOrderAscIdAsc(novel.getId())
                .stream().map(ChapterService::toMeta).toList();
    }

    @Transactional
    public VO create(Long novelId, User user, SaveReq req) {
        Novel novel = novelService.loadOwned(novelId, user);
        Chapter chapter = new Chapter();
        chapter.setNovelId(novel.getId());
        chapter.setTitle(defaultTitle(req.title()));
        chapter.setContent(req.content() == null ? "" : req.content());
        int maxOrder = chapterRepository.findTopByNovelIdOrderByChapterOrderDesc(novel.getId())
                .map(Chapter::getChapterOrder).orElse(0);
        chapter.setChapterOrder(maxOrder + 1);
        chapter.setWordCount((long) countWords(chapter.getContent()));
        chapterRepository.save(chapter);
        novel.setWordCount(novel.getWordCount() + chapter.getWordCount());
        return toVO(chapter);
    }

    @Transactional(readOnly = true)
    public VO get(Long chapterId, User user) {
        return toVO(loadOwned(chapterId, user));
    }

    @Transactional
    public VO save(Long chapterId, User user, SaveReq req) {
        Chapter chapter = loadOwned(chapterId, user);
        Novel novel = novelService.loadOwned(chapter.getNovelId(), user);
        long oldCount = chapter.getWordCount();
        if (req.title() != null && !req.title().isBlank()) {
            chapter.setTitle(req.title());
        }
        if (req.content() != null) {
            chapter.setContent(req.content());
        }
        chapter.setWordCount((long) countWords(chapter.getContent()));
        novel.setWordCount(Math.max(0, novel.getWordCount() + (chapter.getWordCount() - oldCount)));
        return toVO(chapter);
    }

    @Transactional
    public void delete(Long chapterId, User user) {
        Chapter chapter = loadOwned(chapterId, user);
        Novel novel = novelService.loadOwned(chapter.getNovelId(), user);
        novel.setWordCount(Math.max(0, novel.getWordCount() - chapter.getWordCount()));
        chapterRepository.delete(chapter);
    }

    public Chapter loadOwned(Long chapterId, User user) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BizException(404, "章节不存在", 404));
        novelService.loadOwned(chapter.getNovelId(), user);
        return chapter;
    }

    static int countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.replaceAll("\\s+", "").length();
    }

    private static String defaultTitle(String title) {
        return title == null || title.isBlank() ? "未命名章节" : title;
    }

    private static MetaVO toMeta(Chapter c) {
        return new MetaVO(c.getId(), c.getNovelId(), c.getTitle(), c.getChapterOrder(),
                c.getWordCount(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private static VO toVO(Chapter c) {
        return new VO(c.getId(), c.getNovelId(), c.getTitle(), c.getChapterOrder(),
                c.getWordCount(), c.getContent(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
