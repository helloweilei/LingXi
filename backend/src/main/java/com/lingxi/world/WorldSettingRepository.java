package com.lingxi.world;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldSettingRepository extends JpaRepository<WorldSetting, Long> {

    List<WorldSetting> findByNovelIdOrderByCreatedAtAscIdAsc(Long novelId);

    void deleteByNovelId(Long novelId);
}
