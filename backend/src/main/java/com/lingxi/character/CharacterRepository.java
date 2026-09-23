package com.lingxi.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<NovelCharacter, Long> {

    List<NovelCharacter> findByNovelIdOrderByCreatedAtAscIdAsc(Long novelId);

    void deleteByNovelId(Long novelId);
}
