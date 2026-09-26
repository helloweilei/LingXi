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
 * 故事脑：整本书的动态剧情状态文档（已发生的关键事件、未回收的伏笔、
 * 人物关系与弧光变化），由 AI 随章节推进更新；每本书一份（novel_id 唯一）。
 */
@Entity
@Table(name = "story_memories")
public class StoryMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false, unique = true)
    private Long novelId;

    @Lob
    @Column(name = "story_brain", nullable = false)
    private String storyBrain;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
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

    public String getStoryBrain() {
        return storyBrain;
    }

    public void setStoryBrain(String storyBrain) {
        this.storyBrain = storyBrain;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
