package com.lingxi.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterOutlineRepository extends JpaRepository<ChapterOutline, Long> {

    /** 全书大纲行，按规划序号升序（大纲是独立规划行，不再依赖章节表） */
    List<ChapterOutline> findByNovelIdOrderByChapterOrderAsc(Long novelId);

    /** 按规划序号定位一行（生成正文时据此落地章节 / 追加规划） */
    Optional<ChapterOutline> findByNovelIdAndChapterOrder(Long novelId, Integer chapterOrder);

    /** 该书最后一个规划序号，用于新增大纲时接续编号；无大纲返回空 */
    Optional<ChapterOutline> findTopByNovelIdOrderByChapterOrderDesc(Long novelId);

    Optional<ChapterOutline> findByChapterId(Long chapterId);

    void deleteByNovelId(Long novelId);
}
