package com.lingxi.guide.dto;

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

    public record GuideStateVO(String status, String seed, Map<String, String> answers,
                               List<ProposalVO> proposals, Integer selectedId) {
    }

    public record SelectResult(Long novelId) {
    }
}
