package com.lingxi.novel;

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

@Entity
@Table(name = "novels")
public class Novel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(length = 30)
    private String genre;

    /** 草稿 / 连载中 / 已完结 */
    @Column(length = 20)
    private String status;

    @Column(name = "target_words")
    private Integer targetWords;

    @Column(name = "word_count", nullable = false)
    private Long wordCount = 0L;

    @Lob
    @Column(name = "outline")
    private String outline;

    @Column(name = "cover_color", length = 20)
    private String coverColor;

    /** 封面图片静态资源路径（/uploads/xxx.png），空/空串表示未设置 */
    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /** 封面提示词：AI 依据作品设定生成，可编辑；用于外部绘图/复现封面 */
    @Column(name = "cover_prompt", length = 2000)
    private String coverPrompt;

    /** 作者名（封面署名；各发布平台笔名可能不同），空/空串表示未设置 */
    @Column(name = "author_name", length = 30)
    private String authorName;

    /** 语言风格（AI 写作文风约束），多个风格以「、」连接；空/空串表示未设置 */
    @Lob
    @Column(name = "language_style")
    private String languageStyle;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTargetWords() {
        return targetWords;
    }

    public void setTargetWords(Integer targetWords) {
        this.targetWords = targetWords;
    }

    public Long getWordCount() {
        return wordCount;
    }

    public void setWordCount(Long wordCount) {
        this.wordCount = wordCount;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public String getCoverColor() {
        return coverColor;
    }

    public void setCoverColor(String coverColor) {
        this.coverColor = coverColor;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getCoverPrompt() {
        return coverPrompt;
    }

    public void setCoverPrompt(String coverPrompt) {
        this.coverPrompt = coverPrompt;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getLanguageStyle() {
        return languageStyle;
    }

    public void setLanguageStyle(String languageStyle) {
        this.languageStyle = languageStyle;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
