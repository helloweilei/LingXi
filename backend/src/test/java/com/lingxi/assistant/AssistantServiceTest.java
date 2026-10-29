package com.lingxi.assistant;

import com.lingxi.ai.LlmClient;
import com.lingxi.ai.LlmClientProvider;
import com.lingxi.user.User;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 创作助手的对话清洗与时间线渲染：无状态接口的输入边界。
 */
class AssistantServiceTest {

    private final AssistantService service = new AssistantService(mock());

    private static AssistantService.ChatMsg msg(String role, String content) {
        return new AssistantService.ChatMsg(role, content);
    }

    @Test
    void sanitizeDropsBlankAndNormalizesRole() {
        // List.of 不接受 null 元素，而 null 防御正是要测的行为，用 ArrayList 构造
        List<AssistantService.ChatMsg> in = new ArrayList<>();
        in.add(msg("user", "  "));
        in.add(null);
        in.add(msg("USER", "我想写仙侠"));
        in.add(msg("assistant", null));
        in.add(msg("tool", "……"));
        in.add(msg("assistant", "好的，主角是谁？"));

        List<AssistantService.ChatMsg> out = AssistantService.sanitize(in);

        assertEquals(3, out.size());
        assertEquals("user", out.get(0).role());
        assertEquals("我想写仙侠", out.get(0).content());
        // 未识别的角色一律按用户处理，防止注入“助手：”前缀混淆时间线
        assertEquals("user", out.get(1).role());
        assertEquals("assistant", out.get(2).role());
    }

    @Test
    void sanitizeThrowsOnEmptyConversation() {
        assertThrows(Exception.class, () -> AssistantService.sanitize(null));
        assertThrows(Exception.class, () -> AssistantService.sanitize(List.of()));
        assertThrows(Exception.class, () -> AssistantService.sanitize(List.of(msg("user", "   "))));
    }

    @Test
    void sanitizeKeepsOnlyRecentMessagesWhenOverLimit() {
        List<AssistantService.ChatMsg> longConversation = new ArrayList<>();
        for (int i = 1; i <= AssistantService.MAX_MESSAGES + 5; i++) {
            longConversation.add(msg("user", "第 " + i + " 条"));
        }
        List<AssistantService.ChatMsg> out = AssistantService.sanitize(longConversation);

        assertEquals(AssistantService.MAX_MESSAGES, out.size());
        // 保留的是最近的部分：最早 5 条被丢弃
        assertEquals("第 6 条", out.get(0).content());
        assertEquals("第 " + (AssistantService.MAX_MESSAGES + 5) + " 条",
                out.get(out.size() - 1).content());
    }

    @Test
    void transcriptRendersTimelineWithRolePrefix() {
        String text = AssistantService.transcript(List.of(
                msg("user", "想写都市异能"),
                msg("assistant", "好的，主角设定是什么？")));

        assertTrue(text.startsWith("【对话记录"));
        assertTrue(text.contains("用户：想写都市异能"));
        assertTrue(text.contains("助手：好的，主角设定是什么？"));
        // 时间线顺序保持不变
        assertTrue(text.indexOf("想写都市异能") < text.indexOf("主角设定"));
    }

    /* ---------- 开场引导问题 ---------- */

    @Test
    void parseOpenersStripsNumberingAndQuotes() {
        String text = "1、写规则怪谈，帮我搭世界观\n"
                + "2. 「都市异能」从哪个觉醒瞬间切入？\n"
                + "三、想写科幻末日，求主线建议\n"
                + "\n"
                + "· 多余的第四条\n";

        List<String> out = AssistantService.parseOpeners(text);

        assertEquals(3, out.size());
        assertEquals("写规则怪谈，帮我搭世界观", out.get(0));
        assertEquals("都市异能」从哪个觉醒瞬间切入？", out.get(1));
        assertEquals("想写科幻末日，求主线建议", out.get(2));
    }

    @Test
    void parseOpenersSkipsBlankAndOverlongLines() {
        String overlong = "这".repeat(AssistantService.MAX_OPENER_CHARS + 1);
        List<String> out = AssistantService.parseOpeners("  \n" + overlong + "\n- 合法的一条\n");

        assertEquals(1, out.size());
        assertEquals("合法的一条", out.get(0));
        assertTrue(AssistantService.parseOpeners(null).isEmpty());
        assertTrue(AssistantService.parseOpeners("   ").isEmpty());
    }

    @Test
    void hotOpenersReplacesTemplateWhenModelSucceeds() {
        LlmClient client = mock();
        when(client.stream(any(), any(), any(), any())).thenReturn(Flux.just(
                "1、规则怪谈流行的今天，\n", "帮你搭一个无限流开局\n",
                "2. 都市异能怎么开金手指\n", "3、科幻末日种田流怎么破局\n", "4、多余的一条\n"));
        LlmClientProvider provider = mock();
        when(provider.forUser(any())).thenReturn(client);
        AssistantService service = new AssistantService(provider);

        List<String> out = service.hotOpeners(new User());

        // 模型按“一行一条”输出，逐行解析取前 3 条，并剥掉行尾标点
        assertEquals(3, out.size());
        assertEquals("规则怪谈流行的今天", out.get(0));
        assertEquals("帮你搭一个无限流开局", out.get(1));
        assertEquals("都市异能怎么开金手指", out.get(2));
    }

    @Test
    void hotOpenersFallsBackToTemplateWhenModelFails() {
        LlmClientProvider provider = mock();
        when(provider.forUser(any())).thenThrow(new RuntimeException("model down"));
        AssistantService service = new AssistantService(provider);

        List<String> out = service.hotOpeners(new User());

        assertEquals(AssistantService.FALLBACK_OPENERS, out);
    }

    @Test
    void hotOpenersFallsBackWhenOutputUnparsable() {
        LlmClient client = mock();
        // 模型输出空内容：解析不出任何行，同样走兜底
        when(client.stream(any(), any(), any(), any())).thenReturn(Flux.just("  \n", "\n"));
        LlmClientProvider provider = mock();
        when(provider.forUser(any())).thenReturn(client);
        AssistantService service = new AssistantService(provider);

        List<String> out = service.hotOpeners(new User());

        assertEquals(AssistantService.FALLBACK_OPENERS, out);
    }
}
