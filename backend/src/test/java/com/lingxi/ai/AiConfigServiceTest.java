package com.lingxi.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架测试：用户级配置的掩码逻辑与生效判定。
 */
class AiConfigServiceTest {

    @Test
    void maskShortKeyKeepsOnlyEdges() {
        String masked = AiConfigService.mask("abcdefgh");
        assertEquals("ab****gh", masked);
    }

    @Test
    void maskLongKeyKeepsThreeEdges() {
        String masked = AiConfigService.mask("sk-1234567890abcdef");
        assertTrue(masked.startsWith("sk-"));
        assertTrue(masked.endsWith("def"));
        assertTrue(masked.contains("****"));
        assertFalse(masked.contains("1234567890"));
    }

    @Test
    void mockProviderNeverRequiresKey() {
        // provider=mock 时无需任何配置即可生效（对应用户未配置时的默认行为）
        assertEquals("mock", new AiConfig().getProvider());
    }
}
