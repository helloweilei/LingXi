package com.lingxi.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {

    Optional<AiConfig> findByUserId(Long userId);
}
