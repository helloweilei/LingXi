package com.lingxi.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 自动创作任务：大纲 → 逐章生成 → 可选摘要 的一键流水线。
 * 状态机：QUEUED → RUNNING →（CONFIRM 模式逐章）WAITING_APPROVAL → RUNNING → … → DONE/FAILED/CANCELLED
 */
@Entity
@Table(name = "writing_tasks")
public class WritingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String mode; // AUTO / CONFIRM

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WritingTaskStatus status;

    @Lob
    @Column
    private String params; // JSON：{chapters, wordsPerChapter, genOutline, autoSummary}

    @Column(name = "total_chapters", nullable = false)
    private Integer totalChapters = 0;

    @Column(name = "current_chapter", nullable = false)
    private Integer currentChapter = 0;

    @Column(name = "current_title", nullable = false, length = 120)
    private String currentTitle = "";

    @Lob
    @Column
    private String error;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public WritingTaskStatus getStatus() {
        return status;
    }

    public void setStatus(WritingTaskStatus status) {
        this.status = status;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public Integer getTotalChapters() {
        return totalChapters;
    }

    public void setTotalChapters(Integer totalChapters) {
        this.totalChapters = totalChapters;
    }

    public Integer getCurrentChapter() {
        return currentChapter;
    }

    public void setCurrentChapter(Integer currentChapter) {
        this.currentChapter = currentChapter;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public void setCurrentTitle(String currentTitle) {
        this.currentTitle = currentTitle;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
