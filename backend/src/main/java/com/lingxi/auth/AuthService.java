package com.lingxi.auth;

import com.lingxi.auth.dto.AuthDtos.AuthResp;
import com.lingxi.auth.dto.AuthDtos.LoginReq;
import com.lingxi.auth.dto.AuthDtos.RegisterReq;
import com.lingxi.auth.dto.AuthDtos.UserVO;
import com.lingxi.common.BizException;
import com.lingxi.security.JwtService;
import com.lingxi.user.User;
import com.lingxi.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    public AuthResp register(RegisterReq req, String rateKey) {
        rateLimiter.check(rateKey);
        if (userRepository.existsByUsername(req.username())) {
            rateLimiter.onFailure(rateKey);
            throw new BizException("用户名已被占用，换一个试试");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname() == null || req.nickname().isBlank() ? req.username() : req.nickname());
        userRepository.save(user);
        rateLimiter.onSuccess(rateKey);
        return new AuthResp(jwtService.issue(user.getUsername()), toVO(user));
    }

    public AuthResp login(LoginReq req, String rateKey) {
        rateLimiter.check(rateKey);
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> {
                    rateLimiter.onFailure(rateKey);
                    return new BizException("用户名或密码错误");
                });
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            rateLimiter.onFailure(rateKey);
            throw new BizException("用户名或密码错误");
        }
        rateLimiter.onSuccess(rateKey);
        return new AuthResp(jwtService.issue(user.getUsername()), toVO(user));
    }

    public UserVO me(String username) {
        return userRepository.findByUsername(username).map(AuthService::toVO)
                .orElseThrow(() -> new BizException(401, "用户不存在", 401));
    }

    private static UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getUsername(),
                user.getNickname() == null ? user.getUsername() : user.getNickname());
    }
}
