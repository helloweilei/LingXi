package com.lingxi.chapter;

import com.lingxi.chapter.dto.ChapterDtos.MetaVO;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.chapter.dto.ChapterDtos.VO;
import com.lingxi.chapter.dto.ChapterDtos.VersionVO;
import com.lingxi.common.BizException;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChapterService {

    /** 每章保留的最大快照数，超出后丢弃最旧版本 */
    private static final int MAX_VERSIONS_PER_CHAPTER = 50;

    private final ChapterRepository chapterRepository;
    private final ChapterVersionRepository versionRepository;
    private final NovelService novelService;

    public ChapterService(ChapterRepository chapterRepository, ChapterVersionRepository versionRepository,
                          NovelService novelService) {
        this.chapterRepository = chapterRepository;
        this.versionRepository = versionRepository;
        this.novelService = novelService;
    }

    @Transactional(readOnly = true)
    public List<MetaVO> list(Long novelId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        return chapterRepository.findByNovelIdAndDeletedFalseOrderByChapterOrderAscIdAsc(novel.getId())
                .stream().map(ChapterService::toMeta).toList();
    }

    @Transactional
    public VO create(Long novelId, User user, SaveReq req) {
        Novel novel = novelService.loadOwned(novelId, user);
        Chapter chapter = new Chapter();
        chapter.setNovelId(novel.getId());
        chapter.setTitle(defaultTitle(req.title()));
        chapter.setContent(req.content() == null ? "" : req.content());
        int maxOrder = chapterRepository.findTopByNovelIdAndDeletedFalseOrderByChapterOrderDesc(novel.getId())
                .map(Chapter::getChapterOrder).orElse(0);
        chapter.setChapterOrder(maxOrder + 1);
        chapter.setWordCount((long) countWords(chapter.getContent()));
        chapterRepository.save(chapter);
        novel.setWordCount(novel.getWordCount() + chapter.getWordCount());
        return toVO(chapter);
    }

    @Transactional(readOnly = true)
    public VO get(Long chapterId, User user) {
        Chapter chapter = loadOwned(chapterId, user);
        if (chapter.isDeleted()) {
            throw new BizException(404, "章节不存在", 404);
        }
        return toVO(chapter);
    }

    @Transactional
    public VO save(Long chapterId, User user, SaveReq req) {
        Chapter chapter = loadOwned(chapterId, user);
        if (chapter.isDeleted()) {
            throw new BizException(404, "章节不存在", 404);
        }
        Novel novel = novelService.loadOwned(chapter.getNovelId(), user);
        boolean changed = (req.title() != null && !req.title().isBlank() && !req.title().equals(chapter.getTitle()))
                || (req.content() != null && !req.content().equals(chapter.getContent()));
        if (changed && hasKeepableHistory(chapter)) {
            snapshot(chapter, normalizeReason(req.reason(), "save"));
        }
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

    /** 软删除：章节标记删除但保留正文与版本快照，可恢复 */
    @Transactional
    public void delete(Long chapterId, User user) {
        Chapter chapter = loadOwned(chapterId, user);
        if (chapter.isDeleted()) {
            return;
        }
        Novel novel = novelService.loadOwned(chapter.getNovelId(), user);
        chapter.setDeleted(true);
        novel.setWordCount(Math.max(0, novel.getWordCount() - chapter.getWordCount()));
    }

    /** 恢复已软删除的章节 */
    @Transactional
    public void restore(Long chapterId, User user) {
        Chapter chapter = loadOwned(chapterId, user);
        if (!chapter.isDeleted()) {
            return;
        }
        Novel novel = novelService.loadOwned(chapter.getNovelId(), user);
        chapter.setDeleted(false);
        novel.setWordCount(novel.getWordCount() + chapter.getWordCount());
    }

    @Transactional(readOnly = true)
    public List<VersionVO> versions(Long chapterId, User user) {
        loadOwned(chapterId, user);
        return versionRepository.findByChapterIdOrderByVersionNoDesc(chapterId)
                .stream().map(ChapterService::toVersionVO).toList();
    }

    /** 用指定快照覆盖章节（恢复前自动留档当前状态，保证可再回滚） */
    @Transactional
    public VO restoreVersion(Long chapterId, Long versionId, User user) {
        Chapter chapter = loadOwned(chapterId, user);
        Novel novel = novelService.loadOwned(chapter.getNovelId(), user);
        ChapterVersion version = versionRepository.findById(versionId)
                .filter(v -> v.getChapterId().equals(chapter.getId()))
                .orElseThrow(() -> new BizException(404, "版本不存在", 404));
        if (hasKeepableHistory(chapter)) {
            snapshot(chapter, "restore");
        }
        long oldCount = chapter.getWordCount();
        chapter.setTitle(version.getTitle());
        chapter.setContent(version.getContent());
        chapter.setWordCount((long) countWords(chapter.getContent()));
        novel.setWordCount(Math.max(0, novel.getWordCount() + (chapter.getWordCount() - oldCount)));
        return toVO(chapter);
    }

    /** 校验属主后返回实体（含已删除章节，供恢复/版本浏览使用） */
    public Chapter loadOwned(Long chapterId, User user) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BizException(404, "章节不存在", 404));
        novelService.loadOwned(chapter.getNovelId(), user);
        return chapter;
    }

    private boolean hasKeepableHistory(Chapter chapter) {
        return versionRepository.existsByChapterId(chapter.getId())
                || (chapter.getContent() != null && !chapter.getContent().isBlank());
    }

    private void snapshot(Chapter chapter, String reason) {
        int nextNo = versionRepository.findTopByChapterIdOrderByVersionNoDesc(chapter.getId())
                .map(v -> v.getVersionNo() + 1).orElse(1);
        ChapterVersion version = new ChapterVersion();
        version.setChapterId(chapter.getId());
        version.setVersionNo(nextNo);
        version.setTitle(chapter.getTitle());
        version.setContent(chapter.getContent() == null ? "" : chapter.getContent());
        version.setWordCount(chapter.getWordCount());
        version.setReason(reason);
        versionRepository.save(version);
        prune(chapter.getId());
    }

    /** 每章最多保留 MAX_VERSIONS_PER_CHAPTER 个快照，超出删除最旧的 */
    private void prune(Long chapterId) {
        long count = versionRepository.countByChapterId(chapterId);
        if (count <= MAX_VERSIONS_PER_CHAPTER) {
            return;
        }
        List<ChapterVersion> oldest = versionRepository.findByChapterIdOrderByVersionNoAsc(chapterId)
                .stream().limit(count - MAX_VERSIONS_PER_CHAPTER).toList();
        versionRepository.deleteAll(oldest);
    }

    private static String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
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

    private static VersionVO toVersionVO(ChapterVersion v) {
        return new VersionVO(v.getId(), v.getVersionNo(), v.getTitle(), v.getWordCount(),
                v.getReason(), v.getCreatedAt());
    }
}
