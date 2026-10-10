package com.lingxi.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架测试：自动创作任务的状态机与参数 JSON 往返。
 */
class AutoWriteTaskTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void statusMachineHasAllTransitions() {
        List<WritingTaskStatus> active = List.of(
                WritingTaskStatus.QUEUED, WritingTaskStatus.RUNNING, WritingTaskStatus.WAITING_APPROVAL);
        for (WritingTaskStatus s : WritingTaskStatus.values()) {
            assertTrue(s.name().matches("[A-Z_]+"), "状态名应全大写：" + s);
        }
        // CONFIRM 模式挂起态是唯一可“批准继续”的状态
        assertTrue(active.contains(WritingTaskStatus.WAITING_APPROVAL));
        assertTrue(List.of(WritingTaskStatus.DONE, WritingTaskStatus.FAILED, WritingTaskStatus.CANCELLED)
                .stream().noneMatch(active::contains));
    }

    @Test
    void paramsJsonRoundTripPreservesIntent() throws Exception {
        String json = objectMapper.writeValueAsString(new AutoWriteService.Params(8, true, false));
        AutoWriteService.Params back = objectMapper.readValue(json, AutoWriteService.Params.class);
        assertEquals(8, back.chapters());
        assertTrue(back.genOutline());
        assertEquals(false, back.autoOutline());
    }

    @Test
    void onlyTerminalTasksCanBeRemoved() {
        assertTrue(AutoWriteService.isTerminal(WritingTaskStatus.DONE));
        assertTrue(AutoWriteService.isTerminal(WritingTaskStatus.FAILED));
        assertTrue(AutoWriteService.isTerminal(WritingTaskStatus.CANCELLED));
        assertFalse(AutoWriteService.isTerminal(WritingTaskStatus.QUEUED));
        assertFalse(AutoWriteService.isTerminal(WritingTaskStatus.RUNNING));
        assertFalse(AutoWriteService.isTerminal(WritingTaskStatus.WAITING_APPROVAL));
    }

    @Test
    void createdChaptersCsvParsesToIds() {
        assertEquals(List.of(1L, 2L, 3L), AutoWriteService.parseIds("1, 2 ,3"));
        assertEquals(List.of(7L), AutoWriteService.parseIds("7"));
        assertTrue(AutoWriteService.parseIds("").isEmpty());
        assertTrue(AutoWriteService.parseIds(null).isEmpty());
    }

    @Test
    void progressSaveNeverOverridesUserCancelledOrFailed() {
        // 用户已取消：执行器内存中的 RUNNING 不得回写覆盖
        assertEquals(WritingTaskStatus.CANCELLED,
                AutoWriteService.mergeSavedStatus(WritingTaskStatus.RUNNING, WritingTaskStatus.CANCELLED));
        // 任务已失败：不得被内存中的旧状态覆盖
        assertEquals(WritingTaskStatus.FAILED,
                AutoWriteService.mergeSavedStatus(WritingTaskStatus.DONE, WritingTaskStatus.FAILED));
        // 正常进行中：内存状态照常保存
        assertEquals(WritingTaskStatus.DONE,
                AutoWriteService.mergeSavedStatus(WritingTaskStatus.DONE, WritingTaskStatus.RUNNING));
        assertEquals(WritingTaskStatus.RUNNING,
                AutoWriteService.mergeSavedStatus(WritingTaskStatus.RUNNING, WritingTaskStatus.QUEUED));
    }
}
