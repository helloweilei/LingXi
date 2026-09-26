package com.lingxi.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WritingTaskRepository extends JpaRepository<WritingTask, Long> {

    List<WritingTask> findByNovelIdOrderByIdDesc(Long novelId);

    boolean existsByNovelIdAndStatusIn(Long novelId, Collection<WritingTaskStatus> statuses);
}
