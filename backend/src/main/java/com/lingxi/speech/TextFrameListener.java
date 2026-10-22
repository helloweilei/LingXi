package com.lingxi.speech;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * WebSocket 监听器：把分片的文本帧拼成完整消息后交给 onMessage；
 * 连接异常或被对端关闭时让 failure 以异常结束（若调用方尚未拿到结果）。
 */
class TextFrameListener implements WebSocket.Listener {

    private final StringBuilder buffer = new StringBuilder();
    private final Consumer<String> onMessage;
    private final CompletableFuture<String> failure;

    TextFrameListener(Consumer<String> onMessage, CompletableFuture<String> failure) {
        this.onMessage = onMessage;
        this.failure = failure;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String message = buffer.toString();
            buffer.setLength(0);
            try {
                onMessage.accept(message);
            } catch (Exception e) {
                failure.completeExceptionally(e);
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        failure.completeExceptionally(new IllegalStateException(
                "识别服务连接已关闭（" + statusCode + (reason == null || reason.isBlank() ? "" : "：" + reason) + "）"));
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        failure.completeExceptionally(error);
    }
}
