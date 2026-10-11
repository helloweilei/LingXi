package com.lingxi.memory;

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
 * 章节大纲批量生成任务：一次生成多章大纲耗时较长（每章一次 LLM 调用），
 * 放入后台线程池执行，前端轮询进度，避免 HTTP 请求超时。
 * 状态机：QUEUED → RUNNING → DONE / FAILED / CANCELLED
 */
@Entity
@Table(name = "outline_tasks")
public class OutlineTask {

    public enum Status {
        QUEUED, RUNNING, DONE, FAILED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** 待生成大纲的章节 id（逗号分隔），空表示"全书尚未生成大纲的章节" */
    @Column(name = "chapter_ids", nullable = false, length = 1000)
    private String chapterIds = "";

    @Column(nullable = false)
    private Integer total = 0;

    /** 已处理到第几个（0 表示还没开始） */
    @Column(name = "current_index", nullable = false)
    private Integer currentIndex = 0;

    @Column(name = "current_title", nullable = false, length = 120)
    private String currentTitle = "";

    @Column(nullable = false)
    private Integer succeeded = 0;

    @Column(nullable = false)
    private Integer failed = 0;

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

    public boolean isTerminal() {
        return status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getChapterIds() {
        return chapterIds;
    }

    public void setChapterIds(String chapterIds) {
        this.chapterIds = chapterIds == null ? "" : chapterIds;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(Integer currentIndex) {
        this.currentIndex = currentIndex;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public void setCurrentTitle(String currentTitle) {
        this.currentTitle = currentTitle == null ? "" : currentTitle;
    }

    public Integer getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(Integer succeeded) {
        this.succeeded = succeeded;
    }

    public Integer getFailed() {
        return failed;
    }

    public void setFailed(Integer failed) {
        this.failed = failed;
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