package com.lingxi.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 伏笔账本仓库：按埋设章序排列，便于「按故事时间线」浏览与注入。
 */
public interface ForeshadowingRepository extends JpaRepository<Foreshadowing, Long> {

    List<Foreshadowing> findByNovelIdOrderByPlantedOrderAscIdAsc(Long novelId);

    /** 生成 prompt 只注入未回收条目 */
    List<Foreshadowing> findByNovelIdAndStatusOrderByPlantedOrderAscIdAsc(Long novelId, String status);

    long deleteByNovelId(Long novelId);
}
