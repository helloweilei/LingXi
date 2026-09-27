package com.lingxi.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByNovelIdAndDeletedFalseOrderByChapterOrderAscIdAsc(Long novelId);

    Optional<Chapter> findTopByNovelIdAndDeletedFalseOrderByChapterOrderDesc(Long novelId);

    /** 上一章（同书、未删除、序号更小）的最近一条，LIMIT 1，避免全表加载 */
    Optional<Chapter> findFirstByNovelIdAndDeletedFalseAndChapterOrderLessThanOrderByChapterOrderDescIdDesc(
            Long novelId, Integer chapterOrder);

    /** 按序号查章节（供自动创作续跑时定位已生成/空壳章节） */
    Optional<Chapter> findFirstByNovelIdAndDeletedFalseAndChapterOrder(Long novelId, Integer chapterOrder);

    long countByNovelIdAndDeletedFalse(Long novelId);

    void deleteByNovelId(Long novelId);

    @Query("select c.novelId, count(c) from Chapter c where c.novelId in :novelIds and c.deleted = false group by c.novelId")
    List<Object[]> countGroupedByNovelId(@Param("novelIds") List<Long> novelIds);

    @Query("select c.id from Chapter c where c.novelId = :novelId")
    List<Long> findIdsByNovelId(@Param("novelId") Long novelId);
}
