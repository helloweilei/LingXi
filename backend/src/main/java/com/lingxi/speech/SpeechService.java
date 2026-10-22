package com.lingxi.speech;

import com.lingxi.common.BizException;
import com.lingxi.speech.dto.SpeechDtos.ConfigReq;
import com.lingxi.speech.dto.SpeechDtos.ConfigVO;
import com.lingxi.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
public class SpeechService {

    private static final Logger log = LoggerFactory.getLogger(SpeechService.class);

    /** 单次识别音频上限：16k 16bit 单声道 60 秒 */
    private static final int MAX_PCM_BYTES = 16000 * 2 * 60;
    private static final Duration RECOGNIZE_TIMEOUT = Duration.ofSeconds(60);

    private final SpeechConfigRepository repository;
    private final XfyunAsrClient xfyunAsrClient;
    private final AliyunAsrClient aliyunAsrClient;

    public SpeechService(SpeechConfigRepository repository, XfyunAsrClient xfyunAsrClient,
                         AliyunAsrClient aliyunAsrClient) {
        this.repository = repository;
        this.xfyunAsrClient = xfyunAsrClient;
        this.aliyunAsrClient = aliyunAsrClient;
    }

    /** 读取当前用户配置；密钥未填全时视为 browser，前端据此回落到浏览器识别 */
    public ConfigVO get(User user) {
        return repository.findByUserId(user.getId())
                .map(this::toVo)
                .orElse(new ConfigVO("browser", "", "", false, false, null));
    }

    /** 保存当前用户配置；apiKey / apiSecret 留空表示沿用旧值 */
    public ConfigVO save(User user, ConfigReq req) {
        String provider = isBlank(req.provider()) ? "browser" : req.provider();
        SpeechConfig c = repository.findByUserId(user.getId()).orElseGet(() -> {
            SpeechConfig n = new SpeechConfig();
            n.setUserId(user.getId());
            return n;
        });
        String apiKey = isBlank(req.apiKey()) ? c.getApiKey() : req.apiKey().trim();
        String apiSecret = isBlank(req.apiSecret()) ? c.getApiSecret() : req.apiSecret().trim();
        String appId = req.appId() == null ? c.getAppId() : req.appId().trim();
        if ("xfyun".equals(provider) && (isBlank(appId) || isBlank(apiKey) || isBlank(apiSecret))) {
            throw new BizException(400, "科大讯飞需填写 APPID、APIKey 与 APISecret", 400);
        }
        if ("aliyun".equals(provider) && isBlank(apiKey)) {
            throw new BizException(400, "阿里云需填写百炼 API Key", 400);
        }
        // 切换服务商时旧服务的密钥对新服务无意义，必须重新填写
        if (!provider.equals(c.getProvider()) && !"browser".equals(provider)
                && isBlank(req.apiKey())) {
            throw new BizException(400, "切换语音服务后请重新填写密钥", 400);
        }
        c.setProvider(provider);
        c.setAppId(appId);
        c.setApiKey(apiKey);
        c.setApiSecret(apiSecret);
        repository.save(c);
        return toVo(c);
    }

    /** 用用户配置的云服务识别一段 16k 16bit 单声道 PCM 音频 */
    public String recognize(User user, byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            throw new BizException(400, "没有录到声音", 400);
        }
        if (pcm.length > MAX_PCM_BYTES) {
            throw new BizException(400, "单次语音最长 60 秒", 400);
        }
        SpeechConfig cfg = repository.findByUserId(user.getId())
                .filter(c -> !"browser".equals(c.getProvider()))
                .orElseThrow(() -> new BizException(400, "未配置云端语音服务", 400));
        try {
            String text = switch (cfg.getProvider()) {
                case "xfyun" -> xfyunAsrClient.recognize(cfg, pcm, RECOGNIZE_TIMEOUT);
                case "aliyun" -> aliyunAsrClient.recognize(cfg, pcm, RECOGNIZE_TIMEOUT);
                default -> throw new BizException(400, "不支持的语音服务：" + cfg.getProvider(), 400);
            };
            return text == null ? "" : text.trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            Throwable root = unwrap(e);
            if (root instanceof BizException be) {
                throw be;
            }
            if (root instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("speech recognize failed ({}): {}", cfg.getProvider(), root.toString());
            String msg = root instanceof TimeoutException ? "识别超时，请稍后重试"
                    : (root.getMessage() == null ? root.toString() : root.getMessage());
            throw new BizException(502, "语音识别失败：" + msg, 502);
        }
    }

    private static Throwable unwrap(Throwable e) {
        Throwable t = e;
        while ((t instanceof ExecutionException || t instanceof CompletionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private ConfigVO toVo(SpeechConfig c) {
        boolean hasKey = !isBlank(c.getApiKey());
        return new ConfigVO(c.getProvider(), c.getAppId(), hasKey ? mask(c.getApiKey()) : "", hasKey,
                !isBlank(c.getApiSecret()), c.getUpdatedAt());
    }

    /** 掩码：保留首尾少量字符，中间打星 */
    private static String mask(String key) {
        if (key.length() <= 8) {
            return key.charAt(0) + "****" + key.charAt(key.length() - 1);
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 3);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
