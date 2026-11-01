package com.lingxi.ai;

import com.lingxi.character.CharacterRepository;
import com.lingxi.common.BizException;
import com.lingxi.config.AiProperties;
import com.lingxi.memory.ChapterOutlineRepository;
import com.lingxi.memory.StoryMemoryRepository;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import com.lingxi.world.WorldSettingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 依据全书大纲一键生成简介的关键约束：
 * 1. 大纲为空时直接拒绝（400），不浪费一次模型调用；
 * 2. prompt 携带大纲与定位信息（书名/类型/文风/目标字数），但**不带旧简介**——避免模型围着旧文案打转；
 * 3. 任务类型为 DESCRIPTION。
 */
class AiServiceDescriptionTest {

    private final LlmClientProvider provider = mock(LlmClientProvider.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final NovelService novelService = mock(NovelService.class);
    private final AiService aiService = new AiService(
            provider,
            novelService,
            mock(com.lingxi.chapter.ChapterRepository.class),
            mock(com.lingxi.chapter.ChapterService.class),
            mock(CharacterRepository.class),
            mock(WorldSettingRepository.class),
            mock(ChapterOutlineRepository.class),
            mock(StoryMemoryRepository.class),
            mock(VideoPromptRepository.class),
            mock(com.lingxi.memory.ForeshadowingRepository.class),
            new ContextBuilder(new AiProperties()));

    private final User user = mock(User.class);

    @Test
    void blankOutlineIsRejected() {
        when(novelService.loadOwned(1L, user)).thenReturn(new Novel());

        BizException ex = assertThrows(BizException.class, () -> aiService.descriptionFromOutline(1L, user));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void promptCarriesOutlineWithoutOldDescription() {
        when(provider.forUser(any())).thenReturn(llm);
        when(llm.stream(any(), any(), any(), any())).thenReturn(Flux.just("简介"));
        Novel novel = new Novel();
        novel.setTitle("灵犀测试");
        novel.setGenre("玄幻,热血");
        novel.setDescription("这是不应出现在 prompt 里的旧简介");
        novel.setLanguageStyle("轻松幽默");
        novel.setTargetWords(1_000_000);
        novel.setOutline("第一卷：少年觉醒。\n结局卷：终局之战，全书完结。");
        when(novelService.loadOwned(1L, user)).thenReturn(novel);

        aiService.descriptionFromOutline(1L, user).blockLast();

        ArgumentCaptor<AiTaskType> type = ArgumentCaptor.forClass(AiTaskType.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).stream(type.capture(), any(), any(), prompt.capture());
        assertEquals(AiTaskType.DESCRIPTION, type.getValue());

        String p = prompt.getValue();
        assertTrue(p.contains("灵犀测试"));
        assertTrue(p.contains("少年觉醒"));
        assertTrue(p.contains("全书完结"));
        // 定位锚点与输出要求
        assertTrue(p.contains("轻松幽默"));
        assertTrue(p.contains("100-200 字"));
        assertTrue(p.contains("不剧透结局"));
        // 旧简介绝不进 prompt
        assertFalse(p.contains("不应出现在 prompt 里的旧简介"));
    }
}
