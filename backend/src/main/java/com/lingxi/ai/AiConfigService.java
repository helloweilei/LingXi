package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ConfigReq;
import com.lingxi.ai.dto.AiDtos.ConfigVO;
import com.lingxi.ai.dto.AiDtos.TestVO;
import com.lingxi.common.BizException;
import com.lingxi.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Service
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    private static final String SYSTEM_PING = "你是连通性测试助手，请只回复两个字：正常。";

    private final AiConfigRepository aiConfigRepository;
    private final LlmClientProvider llmClientProvider;

    public AiConfigService(AiConfigRepository aiConfigRepository, LlmClientProvider llmClientProvider) {
        this.aiConfigRepository = aiConfigRepository;
        this.llmClientProvider = llmClientProvider;
    }

    /** 读取当前用户配置（apiKey 只回显掩码） */
    public ConfigVO get(User user) {
        AiConfig c = aiConfigRepository.findByUserId(user.getId()).orElse(null);
        if (c == null) {
            return new ConfigVO("mock", "", "", "", false, null);
        }
        return toVo(c);
    }

    /** 保存当前用户配置；apiKey 留空表示沿用旧值 */
    public ConfigVO save(User user, ConfigReq req) {
        String provider = req.provider() == null || req.provider().isBlank() ? "mock" : req.provider();
        if ("openai".equals(provider)) {
            if (isBlank(req.baseUrl()) || isBlank(req.model())) {
                throw new BizException(400, "接入真实模型需填写接口地址与模型名", 400);
            }
            String oldKey = aiConfigRepository.findByUserId(user.getId())
                    .map(AiConfig::getApiKey).orElse("");
            if (isBlank(req.apiKey()) && isBlank(oldKey)) {
                throw new BizException(400, "请填写 API Key（首次配置必填）", 400);
            }
        }
        AiConfig c = aiConfigRepository.findByUserId(user.getId()).orElseGet(() -> {
            AiConfig n = new AiConfig();
            n.setUserId(user.getId());
            return n;
        });
        c.setProvider(provider);
        c.setBaseUrl(req.baseUrl() == null ? "" : req.baseUrl().trim());
        if (!isBlank(req.apiKey())) {
            c.setApiKey(req.apiKey().trim());
        }
        c.setModel(req.model() == null ? "" : req.model().trim());
        aiConfigRepository.save(c);
        return toVo(c);
    }

    /** 连通性测试：mock 秒过；真实模型做一次最小对话请求 */
    public TestVO test(User user) {
        LlmClient client = llmClientProvider.forUser(user);
        if ("mock".equals(client.name())) {
            return new TestVO(true, 0, "示例引擎运行正常（未接入真实模型）");
        }
        long start = System.currentTimeMillis();
        StringBuilder acc = new StringBuilder();
        try {
            String result = client.stream(
                            AiTaskType.CHAT,
                            new AiContext("连通性测试", "", List.of(), "ping"),
                            SYSTEM_PING, "ping")
                    .doOnNext(acc::append)
                    .blockLast(Duration.ofSeconds(30));
            long latency = System.currentTimeMillis() - start;
            String text = (result == null ? acc.toString() : result).trim();
            if (text.isEmpty()) {
                return new TestVO(false, latency, "模型返回为空，请检查模型名是否正确");
            }
            return new TestVO(true, latency, "连接成功，模型响应：" + (text.length() > 30 ? text.substring(0, 30) + "…" : text));
        } catch (Exception e) {
            log.error("AI config test failed: {}", e.toString());
            long latency = System.currentTimeMillis() - start;
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String msg = root.getMessage();
            return new TestVO(false, latency, "连接失败：" + (msg == null ? e.toString() : msg));
        }
    }

    private ConfigVO toVo(AiConfig c) {
        return new ConfigVO(c.getProvider(), c.getBaseUrl(), c.getModel(),
                c.getApiKey().isBlank() ? "" : mask(c.getApiKey()), !c.getApiKey().isBlank(), c.getUpdatedAt());
    }

    /** 掩码：保留首尾 3 字符，中间打星 */
    static String mask(String key) {
        if (key.length() <= 8) {
            return key.substring(0, 2) + "****" + key.substring(key.length() - 2);
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 3);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
