package com.lingxi.guide.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 启发式创作引导相关的请求/响应记录。
 */
public final class GuideDtos {

    private GuideDtos() {
    }

    /** 引导问答的一道题 */
    public record Question(String field, String label, String prompt, List<String> options) {
        public Question(String field, String label, String prompt) {
            this(field, label, prompt, List.of());
        }
    }

    public record StartReq(String seed) {
    }

    public record AnswerReq(String answer) {
    }

    public record SelectReq(int proposalIndex) {
    }

    public record GuideStartVO(Long intentId, String mode, Question firstQuestion) {
    }

    public record AnswerVO(Question question, boolean done, int step, int total) {
    }

    public record PropCharacter(String name, String role, String description) {
    }

    public record PropWorld(String title, String content) {
    }

    public record ProposalVO(String title, String premise, String world, String protagonist,
                             String outline, List<PropCharacter> characters, List<PropWorld> worlds, String platform) {
    }

    /**
     * 引导会话状态：既用于展示，也用于「继续未完成的问答」——
     * step=已完成题数，total=总题数，nextQuestion=下一道待答题（已答完则为 null）。
     */
    public record GuideStateVO(String status, String seed, Map<String, String> answers,
                               List<ProposalVO> proposals, Integer selectedId,
                               int step, int total, Question nextQuestion) {
    }

    public record SelectResult(Long novelId) {
    }

    /**
     * 历史引导会话摘要：供首页「历史方案」入口列表展示。
     * answers 概览保留问答偏好，proposalTitles 为已生成方案的标题列表。
     * novelTitle/novelExists 用于展示该方案已生成的作品（作品可能已被用户删除）。
     */
    public record IntentSummaryVO(Long id, String status, String seed,
                                  Map<String, String> answers,
                                  List<String> proposalTitles,
                                  Integer proposalCount, Integer selectedId,
                                  Long novelId, String novelTitle, boolean novelExists,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
