package com.lingxi.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.BizException;
import com.lingxi.config.AiProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议客户端（DeepSeek / 通义 / Kimi / OpenAI 等，stream=true）。
 */
public class OpenAiCompatClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final double temperature;
    private final long readTimeoutSeconds;

    public OpenAiCompatClient(AiProperties props, ObjectMapper objectMapper) {
        this(props.getBaseUrl(), props.getApiKey(), props.getModel(), props.getTemperature(),
                props.getReadTimeoutSeconds(), props.getConnectTimeoutSeconds(), objectMapper);
    }

    /**
     * 显式参数构造：供用户级配置按需实例化（每个用户可持有独立的 baseUrl/apiKey/model）。
     */
    public OpenAiCompatClient(String baseUrl, String apiKey, String model, double temperature,
                              int readTimeoutSeconds, int connectTimeoutSeconds, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutSeconds * 1000);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.temperature = temperature;
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public Flux<String> stream(AiTaskType type, AiContext context, String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", true,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil(line -> line.trim().endsWith("[DONE]"))
                .mapNotNull(this::extractDelta)
                .filter(s -> !s.isEmpty())
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .onErrorMap(e -> {
                    if (e instanceof BizException) {
                        return e;
                    }
                    log.error("LLM stream failed: {}", e.toString());
                    return new BizException(502, "AI 服务调用失败，请检查网络与 API Key 配置", 502);
                });
    }

    /** 解析上游 SSE 行，提取 choices[0].delta.content */
    private String extractDelta(String line) {
        String s = line.trim();
        if (s.isEmpty() || s.endsWith("[DONE]")) {
            return "";
        }
        if (s.startsWith("data:")) {
            s = s.substring(5).trim();
        }
        if (s.isEmpty() || s.equals("[DONE]")) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(s);
            return node.path("choices").path(0).path("delta").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
