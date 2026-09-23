package com.lingxi.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByNovelIdOrderByChapterOrderAscIdAsc(Long novelId);

    Optional<Chapter> findTopByNovelIdOrderByChapterOrderDesc(Long novelId);

    long countByNovelId(Long novelId);

    void deleteByNovelId(Long novelId);

    @Query("select c.novelId, count(c) from Chapter c where c.novelId in :novelIds group by c.novelId")
    List<Object[]> countGroupedByNovelId(@Param("novelIds") List<Long> novelIds);
}
