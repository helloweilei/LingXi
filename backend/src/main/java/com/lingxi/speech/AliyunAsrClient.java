package com.lingxi.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingxi.common.BizException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云百炼 DashScope「Paraformer 实时语音识别」：wss://dashscope.aliyuncs.com/api-ws/v1/inference/
 * 流程：run-task → 等 task-started → 发送二进制 PCM → finish-task → 收集 sentence_end 的句子直到 task-finished。
 */
@Component
public class AliyunAsrClient {

    private static final String URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/";
    private static final String MODEL = "paraformer-realtime-v2";
    /** 每帧 100ms 音频（16000 * 2 * 0.1） */
    private static final int FRAME_BYTES = 3200;
    /** 按接近实时的节奏送帧，避免服务端因音频过快丢弃；10ms/帧约为 10 倍速 */
    private static final long FRAME_INTERVAL_MS = 10;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AliyunAsrClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String recognize(SpeechConfig cfg, byte[] pcm, Duration timeout) throws Exception {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        StringBuilder text = new StringBuilder();
        CompletableFuture<Void> started = new CompletableFuture<>();
        CompletableFuture<String> result = new CompletableFuture<>();
        TextFrameListener listener = new TextFrameListener(msg -> {
            JsonNode node = readTree(msg);
            JsonNode header = node.path("header");
            switch (header.path("event").asText("")) {
                case "task-started" -> started.complete(null);
                case "result-generated" -> {
                    JsonNode sentence = node.path("payload").path("output").path("sentence");
                    if (sentence.path("sentence_end").asBoolean(false)) {
                        text.append(sentence.path("text").asText(""));
                    }
                }
                case "task-finished" -> result.complete(text.toString());
                case "task-failed" -> {
                    BizException err = new BizException(400, "阿里云识别失败（"
                            + header.path("error_code").asText("") + "）：" + header.path("error_message").asText(""), 400);
                    started.completeExceptionally(err);
                    result.completeExceptionally(err);
                }
                default -> {
                }
            }
        }, result);

        WebSocket ws = httpClient.newWebSocketBuilder()
                .header("Authorization", "bearer " + cfg.getApiKey())
                .buildAsync(URI.create(URL), listener)
                .get(10, TimeUnit.SECONDS);
        try {
            ws.sendText(runTaskJson(taskId), true).join();
            CompletableFuture.anyOf(started, result).get(10, TimeUnit.SECONDS);
            if (result.isCompletedExceptionally()) {
                return result.join();
            }
            for (int offset = 0; offset < pcm.length; offset += FRAME_BYTES) {
                int end = Math.min(pcm.length, offset + FRAME_BYTES);
                ws.sendBinary(ByteBuffer.wrap(pcm, offset, end - offset), true).join();
                if (result.isDone()) {
                    break;
                }
                Thread.sleep(FRAME_INTERVAL_MS);
            }
            ws.sendText(finishTaskJson(taskId), true).join();
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            ws.abort();
        }
    }

    private String runTaskJson(String taskId) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "run-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");
        ObjectNode payload = root.putObject("payload");
        payload.put("task_group", "audio");
        payload.put("task", "asr");
        payload.put("function", "recognition");
        payload.put("model", MODEL);
        ObjectNode params = payload.putObject("parameters");
        params.put("format", "pcm");
        params.put("sample_rate", 16000);
        params.putArray("language_hints").add("zh").add("en");
        payload.putObject("input");
        return root.toString();
    }

    private String finishTaskJson(String taskId) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "finish-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");
        root.putObject("payload").putObject("input");
        return root.toString();
    }

    private JsonNode readTree(String msg) {
        try {
            return objectMapper.readTree(msg);
        } catch (Exception e) {
            throw new IllegalStateException("阿里云返回了无法解析的数据", e);
        }
    }
}
