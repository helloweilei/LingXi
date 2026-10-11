package com.lingxi.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutlineTaskRepository extends JpaRepository<OutlineTask, Long> {

    List<OutlineTask> findTop20ByNovelIdOrderByIdDesc(Long novelId);

    boolean existsByNovelIdAndStatusIn(Long novelId, Collection<OutlineTask.Status> statuses);
}