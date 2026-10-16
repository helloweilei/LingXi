package com.lingxi.ai;

public enum AiTaskType {
    OUTLINE,
    CONTINUE,
    WRITE_CHAPTER,
    POLISH,
    EXPAND,
    CHAT,
    CHAPTER_OUTLINE,
    GUIDE_PROPOSAL,
    COVER_PROMPT,
    CHAPTER_TITLE,
    PROOFREAD,
    /** 去AI化：不改变语义，改写AI腔句子，让行文更自然、更有"人味" */
    DEAI
}
