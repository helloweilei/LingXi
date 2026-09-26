package com.lingxi.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterSummaryRepository extends JpaRepository<ChapterSummary, Long> {

    /** 按章节顺序取全书摘要（过滤已删除章节），供记忆注入 */
    @Query("""
            select s from ChapterSummary s
            join Chapter c on c.id = s.chapterId
            where s.novelId = :novelId and c.deleted = false
            order by c.chapterOrder asc, c.id asc
            """)
    List<ChapterSummary> findByNovelIdOrdered(@Param("novelId") Long novelId);

    Optional<ChapterSummary> findByChapterId(Long chapterId);

    boolean existsByChapterId(Long chapterId);

    void deleteByChapterId(Long chapterId);

    void deleteByNovelId(Long novelId);
}
