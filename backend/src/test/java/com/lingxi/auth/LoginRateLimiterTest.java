package com.lingxi.auth;

import com.lingxi.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 登录限流骨架测试：失败累计 → 锁定 → 成功清零。
 */
class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void allowsRequestsBeforeFailures() {
        limiter.check("u|1.2.3.4");
        limiter.onFailure("u|1.2.3.4");
        limiter.check("u|1.2.3.4");
    }

    @Test
    void locksAfterFiveFailures() {
        String key = "u|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            limiter.onFailure(key);
        }
        assertThatThrownBy(() -> limiter.check(key))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(429);
    }

    @Test
    void locksAfterFiveInterleavedFailures() {
        // 模拟真实登录路径：check -> 失败 -> onFailure 交替发生
        String key = "u|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            limiter.check(key);
            limiter.onFailure(key);
        }
        assertThatThrownBy(() -> limiter.check(key))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(429);
    }

    @Test
    void successResetsFailures() {
        String key = "u|1.2.3.4";
        for (int i = 0; i < 4; i++) {
            limiter.onFailure(key);
        }
        limiter.onSuccess(key);
        limiter.check(key);
    }

    @Test
    void keysAreIsolated() {
        limiter.onFailure("a|1.2.3.4");
        limiter.onFailure("a|1.2.3.4");
        limiter.onFailure("a|1.2.3.4");
        limiter.onFailure("a|1.2.3.4");
        limiter.onFailure("a|1.2.3.4");
        assertThatThrownBy(() -> limiter.check("a|1.2.3.4")).isInstanceOf(BizException.class);
        // 不同 key 不受影响
        limiter.check("b|5.6.7.8");
    }
}
