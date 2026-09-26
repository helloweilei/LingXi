package com.lingxi.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterVersionRepository extends JpaRepository<ChapterVersion, Long> {

    List<ChapterVersion> findByChapterIdOrderByVersionNoAsc(Long chapterId);

    List<ChapterVersion> findByChapterIdOrderByVersionNoDesc(Long chapterId);

    Optional<ChapterVersion> findTopByChapterIdOrderByVersionNoDesc(Long chapterId);

    long countByChapterId(Long chapterId);

    boolean existsByChapterId(Long chapterId);

    void deleteByChapterId(Long chapterId);

    void deleteByChapterIdIn(List<Long> chapterIds);
}
