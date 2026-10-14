package com.lingxi.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 章节大纲：**创作前的规划行**，是章节的上游产物，而不是章节的附属。
 *
 * 依赖方向：大纲（规划） → 生成正文 → 落地为章节。
 * 因此本章大纲自带规划序号（chapterOrder）与规划章节名（title），
 * 不需要先有章节才能存在；chapterId 仅在该章正文已生成时回填，用于把规划与产物对应起来。
 */
@Entity
@Table(name = "chapter_outlines")
public class ChapterOutline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    /** 规划序号（同一本书内从 1 递增），大纲先于章节存在时即已确定 */
    @Column(name = "chapter_order", nullable = false)
    private Integer chapterOrder;

    /** 规划章节名（由用户填写或 AI 生成）；为空时前端显示占位名 */
    @Column(name = "title", length = 120)
    private String title;

    /** 已生成正文的章节 id；尚未生成正文时为 null（规划先行于产物） */
    @Column(name = "chapter_id", unique = true)
    private Long chapterId;

    @Lob
    @Column(name = "outline", nullable = false)
    private String outline;

    /** 本章目标字数（可按章覆盖全书均摊值）；NULL 表示沿用「总目标字数 / 章节数」 */
    @Column(name = "target_words")
    private Integer targetWords;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Integer getChapterOrder() {
        return chapterOrder;
    }

    public void setChapterOrder(Integer chapterOrder) {
        this.chapterOrder = chapterOrder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public Integer getTargetWords() {
        return targetWords;
    }

    public void setTargetWords(Integer targetWords) {
        this.targetWords = targetWords;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
