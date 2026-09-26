package com.lingxi.auth;

import com.lingxi.common.BizException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录/注册失败限流（进程内存版）。
 * 规则：同一 key（用户名+IP）15 分钟内连续失败 5 次，锁定 15 分钟；登录成功即清零。
 * 注意：单实例有效；多实例部署时应替换为 Redis 等共享存储。
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MINUTES = 15;
    private static final long LOCK_MINUTES = 15;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 请求前校验：已锁定时直接抛 429；未锁定则保留计数（由 onFailure 累计） */
    public void check(String key) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return;
        }
        if (a.blockedUntil != null && Instant.now().isBefore(a.blockedUntil)) {
            throw new BizException(429, "尝试次数过多，请 15 分钟后再试", 429);
        }
        // 未锁定或锁已过期：不清空计数，失败次数由 onFailure 继续累计/重置
    }

    /** 记录一次失败；达到阈值后进入锁定状态 */
    public void onFailure(String key) {
        long now = Instant.now().toEpochMilli();
        Attempt a = attempts.compute(key, (k, old) -> {
            Attempt base = old;
            if (base == null || base.blockedUntil != null || now - base.windowStart > WINDOW_MINUTES * 60_000L) {
                return new Attempt(new AtomicInteger(1), now, null);
            }
            base.failures.incrementAndGet();
            return base;
        });
        if (a.failures.get() >= MAX_FAILURES) {
            attempts.computeIfPresent(key, (k, old) ->
                    old.blockedUntil == null && old.failures.get() >= MAX_FAILURES
                            ? new Attempt(old.failures, old.windowStart, Instant.now().plusSeconds(LOCK_MINUTES * 60))
                            : old);
        }
    }

    /** 登录成功：清空该 key 的失败记录 */
    public void onSuccess(String key) {
        attempts.remove(key);
    }

    private record Attempt(AtomicInteger failures, long windowStart, Instant blockedUntil) {
    }
}
