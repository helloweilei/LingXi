package com.lingxi.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoPromptRepository extends JpaRepository<VideoPrompt, Long> {

    /** 某部作品的全部视频提示词（章节分镜 + 宣传片），供列表展示与「已生成」角标 */
    List<VideoPrompt> findByNovelIdOrderByUpdatedAtDesc(Long novelId);

    /** 定位某一章的分镜提示词（重新生成时覆盖更新） */
    Optional<VideoPrompt> findByNovelIdAndChapterId(Long novelId, Long chapterId);

    /** 定位全书宣传片提示词（chapterId 为空） */
    Optional<VideoPrompt> findByNovelIdAndChapterIdIsNull(Long novelId);
}
