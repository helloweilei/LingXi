package com.lingxi.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoryMemoryRepository extends JpaRepository<StoryMemory, Long> {

    Optional<StoryMemory> findByNovelId(Long novelId);

    void deleteByNovelId(Long novelId);
}
