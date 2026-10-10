package com.lingxi.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreativeIntentRepository extends JpaRepository<CreativeIntent, Long> {

    Optional<CreativeIntent> findByIdAndUserId(Long id, Long userId);
}
