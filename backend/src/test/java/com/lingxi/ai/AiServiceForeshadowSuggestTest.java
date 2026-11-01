package com.lingxi.ai;

import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.chapter.ChapterService;
import com.lingxi.character.CharacterRepository;
import com.lingxi.config.AiProperties;
import com.lingxi.memory.ChapterOutlineRepository;
import com.lingxi.memory.Foreshadowing;
import com.lingxi.memory.ForeshadowingRepository;
import com.lingxi.memory.StoryMemoryRepository;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowSuggestVO;
import com.lingxi.novel.NovelService;
import com.lingxi.world.WorldSettingRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 半自动伏笔记账：建议 JSON 解析与 prompt 构造。
 * 解析必须容错（剥代码块/前后噪声）、resolved 只认账本既有 id、new 条数有上限。
 */
class AiServiceForeshadowSuggestTest {

    private final AiService aiService = new AiService(
            mock(LlmClientProvider.class),
            mock(NovelService.class),
            mock(ChapterRepository.class),
            mock(ChapterService.class),
            mock(CharacterRepository.class),
            mock(WorldSettingRepository.class),
            mock(ChapterOutlineRepository.class),
            mock(StoryMemoryRepository.class),
            mock(VideoPromptRepository.class),
            mock(ForeshadowingRepository.class),
            new ContextBuilder(new AiProperties()));

    /** 实体无 setId，测试里用反射注入 id */
    private static Foreshadowing open(long id, String title) {
        try {
            Foreshadowing f = new Foreshadowing();
            f.setNovelId(1L);
            f.setTitle(title);
            f.setStatus(Foreshadowing.STATUS_OPEN);
            Field field = Foreshadowing.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(f, id);
            return f;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void parseStripsCodeFenceNoiseFiltersIdsAndCapsNewItems() {
        List<Foreshadowing> open = List.of(open(1, "玉佩之谜"), open(7, "师父的身世"));
        String raw = """
                好的，以下是分析结果：
                ```json
                {"new":[
                  {"title":" 玉佩的来历 ","content":"主角拾到的玉佩发烫，暗示身世","plantedOrder":0},
                  {"title":"神秘组织","content":"黑衣人提及组织名","plantedOrder":6},
                  {"title":"伏笔C","content":"c","plantedOrder":6},
                  {"title":"伏笔D","content":"超出上限应被丢弃","plantedOrder":6}
                ],
                "resolved":[1,999]}
                ```
                希望对你有帮助。
                """;
        ForeshadowSuggestVO vo = aiService.parseForeshadowSuggest(raw, open, 6);
        // new 至多 3 条；标题去空白；plantedOrder 非法时回填本章序
        assertThat(vo.newItems()).hasSize(3);
        assertThat(vo.newItems().get(0).title()).isEqualTo("玉佩的来历");
        assertThat(vo.newItems().get(0).plantedOrder()).isEqualTo(6);
        assertThat(vo.newItems().get(2).title()).isEqualTo("伏笔C");
        // resolved 只认账本里真实存在的未回收 id；编造的 999 被过滤
        assertThat(vo.resolved()).hasSize(1);
        assertThat(vo.resolved().get(0).id()).isEqualTo(1L);
        assertThat(vo.resolved().get(0).title()).isEqualTo("玉佩之谜");
        assertThat(vo.resolved().get(0).resolvedOrder()).isEqualTo(6);
    }

    @Test
    void parseDegradesToEmptyOnGarbageOrBlank() {
        List<Foreshadowing> open = List.of(open(1, "玉佩之谜"));
        assertThat(aiService.parseForeshadowSuggest("", open, 3).isEmpty()).isTrue();
        assertThat(aiService.parseForeshadowSuggest("模型开小差了，没有 JSON", open, 3).isEmpty()).isTrue();
        assertThat(aiService.parseForeshadowSuggest("```json\n{\"new\": []}\n```", open, 3).isEmpty()).isTrue();
        // 空清单 + 空 new：合法但为空
        ForeshadowSuggestVO vo = aiService.parseForeshadowSuggest("{\"new\":[],\"resolved\":[]}", List.of(), 3);
        assertThat(vo.isEmpty()).isTrue();
    }

    @Test
    void promptContainsChapterContentOpenListAndJsonProtocol() {
        Chapter chapter = new Chapter();
        chapter.setTitle("第六章 夜探宗门");
        chapter.setChapterOrder(6);
        chapter.setContent("主角潜入藏经阁，发现了一枚发烫的玉佩……");
        List<Foreshadowing> open = List.of(open(1, "玉佩之谜"));
        String prompt = aiService.foreshadowSuggestPrompt(chapter, open);
        assertThat(prompt).contains("第六章 夜探宗门");
        assertThat(prompt).contains("发烫的玉佩");
        assertThat(prompt).contains("当前未回收伏笔清单");
        assertThat(prompt).contains("#1 玉佩之谜");
        assertThat(prompt).contains("resolved");
        assertThat(prompt).contains("plantedOrder");
    }

    @Test
    void promptNotesEmptyLedgerWhenNoOpenItems() {
        Chapter chapter = new Chapter();
        chapter.setTitle("第一章");
        chapter.setChapterOrder(1);
        chapter.setContent("开篇。");
        String prompt = aiService.foreshadowSuggestPrompt(chapter, List.of());
        assertThat(prompt).contains("暂无未回收伏笔");
    }
}
