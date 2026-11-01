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
 * 伏笔账本：跨章节悬念的结构化登记。
 *
 * 背景（历史分析结论）：大纲/正文生成只携带「上一章 + 前 3 章概览 + 全书大纲尾部」，
 * 距离超过 3 章的悬念会掉出 AI 的上下文视野。本实体把伏笔变成显式数据，
 * 由 {@link com.lingxi.ai.ContextBuilder} 把 OPEN 状态条目作为
 * 【未回收伏笔清单】注入大纲与正文生成 prompt，使长程伏笔始终在场。
 *
 * 状态机：OPEN（已埋未收）→ RESOLVED（已回收）/ ABANDONED（明确放弃，不再注入）。
 */
@Entity
@Table(name = "foreshadowings")
public class Foreshadowing {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_ABANDONED = "ABANDONED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    /** 伏笔名（简短标识，如「师父的身世之谜」） */
    @Column(name = "title", nullable = false, length = 60)
    private String title;

    /** 埋设描述：埋了什么、以什么形式呈现、回收时应给出什么（可空） */
    @Lob
    @Column(name = "content")
    private String content;

    /** 埋设章序（可空：全书级伏笔未必绑定某一章） */
    @Column(name = "planted_order")
    private Integer plantedOrder;

    /** 预期回收章序（可空：不预设时由作者/AI 后续安排） */
    @Column(name = "expected_order")
    private Integer expectedOrder;

    /** OPEN / RESOLVED / ABANDONED；仅 OPEN 会注入生成 prompt */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_OPEN;

    /** 实际回收章序（标记 RESOLVED 时回填） */
    @Column(name = "resolved_order")
    private Integer resolvedOrder;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPlantedOrder() {
        return plantedOrder;
    }

    public void setPlantedOrder(Integer plantedOrder) {
        this.plantedOrder = plantedOrder;
    }

    public Integer getExpectedOrder() {
        return expectedOrder;
    }

    public void setExpectedOrder(Integer expectedOrder) {
        this.expectedOrder = expectedOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getResolvedOrder() {
        return resolvedOrder;
    }

    public void setResolvedOrder(Integer resolvedOrder) {
        this.resolvedOrder = resolvedOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
