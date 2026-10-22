package com.lingxi.speech;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpeechConfigRepository extends JpaRepository<SpeechConfig, Long> {

    Optional<SpeechConfig> findByUserId(Long userId);
}
