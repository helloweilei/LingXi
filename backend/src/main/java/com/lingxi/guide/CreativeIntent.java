package com.lingxi.guide;

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
 * 启发式创作引导会话：贯穿「问答/种子 → 创意提案 → 选用生成作品」的一次引导。
 */
@Entity
@Table(name = "creative_intents")
public class CreativeIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** COLLECTING（收集中）/ PROPOSING（已出方案）/ READY（已生成作品） */
    @Column(nullable = false, length = 20)
    private String status;

    /** 引导模式存问答答案（字段->答案）；种子模式存 {"seed": "..."} */
    @Lob
    @Column(name = "seed_json")
    private String seedJson;

    /** AI 生成的创意提案 JSON 数组 */
    @Lob
    @Column(name = "proposal_json")
    private String proposalJson;

    /** 选用的提案序号 */
    @Column(name = "selected_id")
    private Integer selectedId;

    /** 采用该方案后生成的作品 id（可为空：尚未采用或作品已被删除） */
    @Column(name = "novel_id")
    private Long novelId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeedJson() {
        return seedJson;
    }

    public void setSeedJson(String seedJson) {
        this.seedJson = seedJson;
    }

    public String getProposalJson() {
        return proposalJson;
    }

    public void setProposalJson(String proposalJson) {
        this.proposalJson = proposalJson;
    }

    public Integer getSelectedId() {
        return selectedId;
    }

    public void setSelectedId(Integer selectedId) {
        this.selectedId = selectedId;
    }

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
