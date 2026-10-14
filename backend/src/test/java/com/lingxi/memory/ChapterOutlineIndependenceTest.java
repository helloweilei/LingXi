package com.lingxi.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大纲与章节的依赖方向：**大纲（规划）→ 章节（产物）**。
 * 规划行自带序号与标题，可以脱离章节独立存在；章节在生成正文时才落地。
 */
class ChapterOutlineIndependenceTest {

    @Test
    void outlineCarriesItsOwnOrderAndTitle() {
        ChapterOutline o = new ChapterOutline();
        o.setNovelId(1L);
        o.setChapterOrder(3);
        o.setTitle("雨夜惊变");
        o.setOutline("1. 开篇…");

        // 尚未生成正文时 chapterId 为空：规划先行于产物
        assertThat(o.getChapterId()).isNull();
        assertThat(o.getChapterOrder()).isEqualTo(3);
        assertThat(o.getTitle()).isEqualTo("雨夜惊变");
    }

    @Test
    void outlineWithoutContentIsStillValid() {
        // 大纲内容允许为空（用户可先占位，之后让 AI 补写），
        // hasOutline 判定为 false，但这一行规划本身是合法的。
        ChapterOutline o = new ChapterOutline();
        o.setNovelId(1L);
        o.setChapterOrder(1);
        o.setOutline("");
        assertThat(o.getOutline()).isEmpty();
    }

    @Test
    void linksToChapterAfterContentGenerated() {
        ChapterOutline o = new ChapterOutline();
        o.setChapterOrder(2);
        o.setChapterId(88L);
        // 生成正文后规划与产物关联，后续重写/续写都复用同一章
        assertThat(o.getChapterId()).isEqualTo(88L);
    }
}
