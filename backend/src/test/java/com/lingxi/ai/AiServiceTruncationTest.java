package com.lingxi.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 截断检测：LLM 单次输出有token 上限，写满后会硬切断（句子停在半截）。
 * writeChapterAndSave / AutoWriteService 依赖该判断触发自动续写，故固化规则。
 */
class AiServiceTruncationTest {

    @Test
    void detectsMissingSentenceEndPunctuation() {
        // 截图中的真实症状：停在"火焰卷过"这种半句话上
        assertTrue(AiService.looksTruncated("他的袖口瞬间被烧穿，火焰卷过"));
        assertTrue(AiService.looksTruncated("这是一段没有结尾标点的文字"));
    }

    @Test
    void treatsCompleteEndingAsNotTruncated() {
        assertFalse(AiService.looksTruncated("火焰卷过纸页，露出一行小字。"));
        assertFalse(AiService.looksTruncated("他终于开口了——「我信你。」"));
        assertFalse(AiService.looksTruncated("这一战的结局，就在这一夜落定！"));
    }

    @Test
    void treatsTooShortLastParagraphAsSuspicious() {
        // 结尾有句号，但最后一段只有几个字，很可能被切在半段
        String text = "前面的正文内容足够长，应当不会被判定为截断。\n\n嗯。";
        assertTrue(AiService.looksTruncated(text));
    }

    @Test
    void blankContentIsTreatedAsTruncated() {
        assertTrue(AiService.looksTruncated(null));
        assertTrue(AiService.looksTruncated(""));
        assertTrue(AiService.looksTruncated("   "));
    }

    @Test
    void multiParagraphCompleteTextIsNotTruncated() {
        String text = "第一段落有完整的内容。\n\n第二段落也有完整的内容，情节推进到位。\n\n第三段落收束本章，矛盾得到结果。";
        assertFalse(AiService.looksTruncated(text));
    }
}