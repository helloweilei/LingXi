package com.lingxi.ai;

import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.chapter.ChapterService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文生视频提示词的关键约束：
 * 1. 单章分镜模式必须携带本章正文与锚点规则（风格锚点 + 角色锚点 + 逐字一致）；
 * 2. 宣传片模式使用全书大纲，不携带章节正文；
 * 3. 任务类型为 VIDEO_PROMPT（Mock 引擎按类型分发，全部类型有非空输出由 MockLlmClientTest 覆盖）。
 */
class AiServiceVideoPromptTest {

    private final LlmClientProvider provider = mock(LlmClientProvider.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final NovelService novelService = mock(NovelService.class);
    private final ChapterRepository chapterRepository = mock(ChapterRepository.class);
    private final VideoPromptRepository videoPromptRepository = mock(VideoPromptRepository.class);
    private final AiService aiService = new AiService(
            provider,
            novelService,
            chapterRepository,
            mock(ChapterService.class),
            mock(CharacterRepository.class),
            mock(WorldSettingRepository.class),
            mock(ChapterOutlineRepository.class),
            mock(StoryMemoryRepository.class),
            videoPromptRepository,
            mock(com.lingxi.memory.ForeshadowingRepository.class),
            new ContextBuilder(new AiProperties()));

    private final User user = mock(User.class);

    private Novel novel() {
        Novel n = new Novel();
        n.setTitle("灵犀测试");
        n.setGenre("玄幻");
        n.setDescription("测试用作品");
        n.setOutline("第一卷：少年觉醒。\n结局卷：终局之战，全书完结。");
        return n;
    }

    @Test
    void storyboardModeCarriesChapterContentAndAnchors() {
        when(provider.forUser(any())).thenReturn(llm);
        when(llm.stream(any(), any(), any(), any())).thenReturn(Flux.just("ok"));
        when(novelService.loadOwned(1L, user)).thenReturn(novel());
        Chapter chapter = new Chapter();
        chapter.setNovelId(1L);
        chapter.setChapterOrder(3);
        chapter.setTitle("雨夜惊变");
        chapter.setContent("雨夜，主角在破庙中遭遇追杀，被迫亮出隐藏的实力。");
        when(chapterRepository.findById(9L)).thenReturn(java.util.Optional.of(chapter));

        aiService.videoPrompt(1L, 9L, user).blockLast();

        ArgumentCaptor<AiTaskType> type = ArgumentCaptor.forClass(AiTaskType.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).stream(type.capture(), any(), any(), prompt.capture());
        assertEquals(AiTaskType.VIDEO_PROMPT, type.getValue());

        String p = prompt.getValue();
        assertTrue(p.contains("本章正文"));
        assertTrue(p.contains("雨夜惊变"));
        assertTrue(p.contains("破庙"));
        assertTrue(p.contains("3-6 个视频分镜"));
        // 锚点协议：风格/角色锚点 + 跨镜头一致性要求 + 双语输出
        assertTrue(p.contains("【风格锚点】"));
        assertTrue(p.contains("【角色锚点】"));
        assertTrue(p.contains("逐字一致"));
        assertTrue(p.contains("英文 Prompt"));
    }

    @Test
    void trailerModeUsesOutlineWithoutChapterContent() {
        when(provider.forUser(any())).thenReturn(llm);
        when(llm.stream(any(), any(), any(), any())).thenReturn(Flux.just("ok"));
        when(novelService.loadOwned(1L, user)).thenReturn(novel());

        aiService.videoPrompt(1L, null, user).blockLast();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).stream(any(), any(), any(), prompt.capture());
        String p = prompt.getValue();
        assertTrue(p.contains("全书大纲"));
        assertTrue(p.contains("终局之战"));
        assertTrue(p.contains("宣传片"));
        assertTrue(p.contains("「灵犀测试」"));
        assertFalse(p.contains("本章正文"));
    }

    @Test
    void streamCompletionUpsertsPersistedPrompt() {
        when(provider.forUser(any())).thenReturn(llm);
        when(llm.stream(any(), any(), any(), any())).thenReturn(Flux.just("【风格锚点】", "…", "【镜头 1】"));
        when(novelService.loadOwned(1L, user)).thenReturn(novel());
        Chapter chapter = new Chapter();
        chapter.setNovelId(1L);
        when(chapterRepository.findById(9L)).thenReturn(java.util.Optional.of(chapter));
        // 该章此前没有生成过 → 新建
        when(videoPromptRepository.findByNovelIdAndChapterId(1L, 9L)).thenReturn(java.util.Optional.empty());

        aiService.videoPrompt(1L, 9L, user).blockLast();

        ArgumentCaptor<VideoPrompt> saved = ArgumentCaptor.forClass(VideoPrompt.class);
        verify(videoPromptRepository).save(saved.capture());
        assertEquals(9L, saved.getValue().getChapterId());
        assertEquals("storyboard", saved.getValue().getMode());
        assertEquals("【风格锚点】…【镜头 1】", saved.getValue().getContent());
    }

    @Test
    void streamCompletionUpdatesExistingPrompt() {
        when(provider.forUser(any())).thenReturn(llm);
        when(llm.stream(any(), any(), any(), any())).thenReturn(Flux.just("新版提示词"));
        when(novelService.loadOwned(1L, user)).thenReturn(novel());
        VideoPrompt existing = new VideoPrompt();
        existing.setNovelId(1L);
        existing.setMode("trailer");
        existing.setContent("旧版");
        // 宣传片模式（chapterId 为空）：命中已有记录 → 覆盖内容而不是新建
        //（Novel 无法注入 id，persistVideoPrompt 传入的 novelId 为 null，用 isNull 匹配）
        when(videoPromptRepository.findByNovelIdAndChapterIdIsNull(org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(java.util.Optional.of(existing));

        aiService.videoPrompt(1L, null, user).blockLast();

        verify(videoPromptRepository).save(existing);
        assertEquals("新版提示词", existing.getContent());
    }

    @Test
    void deleteRejectsPromptOfOtherNovel() {
        when(novelService.loadOwned(1L, user)).thenReturn(novel());
        VideoPrompt other = new VideoPrompt();
        other.setNovelId(2L);
        when(videoPromptRepository.findById(5L)).thenReturn(java.util.Optional.of(other));

        BizException ex = assertThrows(BizException.class, () -> aiService.deleteVideoPrompt(1L, 5L, user));
        assertEquals(404, ex.getHttpStatus());
        verify(videoPromptRepository, never()).delete(any(VideoPrompt.class));
    }
}
