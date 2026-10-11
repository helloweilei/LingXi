package com.lingxi.guide;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreativeIntentRepository extends JpaRepository<CreativeIntent, Long> {

    Optional<CreativeIntent> findByIdAndUserId(Long id, Long userId);

    /** 历史引导会话列表：按用户倒序（最近的在前） */
    List<CreativeIntent> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
