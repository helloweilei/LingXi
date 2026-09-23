package com.lingxi.ai;

import java.util.List;

/**
 * 传给 LLM 客户端的作品上下文摘要：真实引擎可忽略，Mock 引擎用它生成更贴合的示例文案。
 */
public record AiContext(String novelTitle, String genre, List<String> characterNames, String sourceText) {
}
