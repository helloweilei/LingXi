package com.lingxi.novel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NovelRepository extends JpaRepository<Novel, Long> {

    List<Novel> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Novel> findByIdAndUserId(Long id, Long userId);
}
