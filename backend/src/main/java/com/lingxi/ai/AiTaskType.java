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
    /** 文生视频提示词：单章分镜 / 全书宣传片，只产出提示词不调用视频模型 */
    VIDEO_PROMPT,
    CHAPTER_TITLE,
    PROOFREAD,
    /** 去AI化：不改变语义，改写AI腔句子，让行文更自然、更有"人味" */
    DEAI,
    /** 根据角色设定与作品大纲推理人物关系，输出 JSON 数组 */
    CHARACTER_RELATION,
    /** 编辑作品时依据全书大纲一键生成作品简介 */
    DESCRIPTION,
    /** 正文生成完成后的伏笔记账建议：从本章正文提炼新埋伏笔与已回收伏笔，输出 JSON */
    FORESHADOW_SUGGEST
}
