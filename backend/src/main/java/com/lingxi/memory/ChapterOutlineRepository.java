package com.lingxi.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterOutlineRepository extends JpaRepository<ChapterOutline, Long> {

    /** 按章节顺序取全书大纲（过滤已删除章节），供记忆注入与规划视图 */
    @Query("""
            select o from ChapterOutline o
            join Chapter c on c.id = o.chapterId
            where o.novelId = :novelId and c.deleted = false
            order by c.chapterOrder asc, c.id asc
            """)
    List<ChapterOutline> findByNovelIdOrdered(@Param("novelId") Long novelId);

    Optional<ChapterOutline> findByChapterId(Long chapterId);

    boolean existsByChapterId(Long chapterId);

    void deleteByChapterId(Long chapterId);

    void deleteByNovelId(Long novelId);
}
