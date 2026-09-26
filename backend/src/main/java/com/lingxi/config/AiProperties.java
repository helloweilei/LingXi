package com.lingxi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lingxi.ai")
public class AiProperties {

    /** mock = 内置离线示例引擎；openai = OpenAI 兼容协议 */
    private String provider = "mock";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private double temperature = 0.8;
    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 180;
    /** 上下文预算（字符，中文约 1 字 ≈ 1 token）：设定/大纲/记忆按优先级打包 */
    private int contextBudgetChars = 8000;
    /** 注入的最近章节摘要条数 */
    private int maxRecentSummaries = 10;

    public boolean isRealLlm() {
        return "openai".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getContextBudgetChars() {
        return contextBudgetChars;
    }

    public void setContextBudgetChars(int contextBudgetChars) {
        this.contextBudgetChars = contextBudgetChars;
    }

    public int getMaxRecentSummaries() {
        return maxRecentSummaries;
    }

    public void setMaxRecentSummaries(int maxRecentSummaries) {
        this.maxRecentSummaries = maxRecentSummaries;
    }
}
