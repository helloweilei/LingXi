package com.lingxi.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRelationRepository extends JpaRepository<CharacterRelation, Long> {

    List<CharacterRelation> findByNovelIdOrderByIdAsc(Long novelId);

    boolean existsByNovelIdAndSourceIdAndTargetIdAndLabel(Long novelId, Long sourceId, Long targetId, String label);

    void deleteBySourceIdOrTargetId(Long sourceId, Long targetId);

    void deleteByNovelId(Long novelId);
}
