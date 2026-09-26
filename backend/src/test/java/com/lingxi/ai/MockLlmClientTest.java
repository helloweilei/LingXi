package com.lingxi.ai;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架测试：内置示例引擎所有任务类型均有非空输出（含 P2 新增的整章写作）。
 */
class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();

    private String collect(AiTaskType type, AiContext ctx) {
        return Flux.from(client.stream(type, ctx, "系统", "任务")).collectList().block().stream()
                .reduce("", String::concat);
    }

    @Test
    void writeChapterProducesFullChapterText() {
        AiContext ctx = new AiContext("测试之书", "玄幻", java.util.List.of("林小满"), "楔子");
        String text = collect(AiTaskType.WRITE_CHAPTER, ctx);
        assertFalse(text.isBlank());
        assertTrue(text.length() > 500, "整章正文应足够长，实际 " + text.length());
        assertTrue(text.contains("林小满"), "正文应代入主角名");
    }

    @Test
    void allTaskTypesProduceNonBlankOutput() {
        AiContext ctx = new AiContext("测试之书", "都市", java.util.List.of("主角"), "原文内容。");
        for (AiTaskType type : AiTaskType.values()) {
            String text = collect(type, ctx);
            assertFalse(text.isBlank(), type + " 不应输出空白");
        }
    }
}
