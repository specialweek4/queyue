package com.specialweek.auth.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.specialweek.auth.api.dto.LoginRequest;
import com.specialweek.auth.api.dto.RegisterRequest;
import com.specialweek.auth.api.dto.ResetPasswordRequest;
import com.specialweek.auth.api.dto.TokenResponse;
import com.specialweek.auth.config.AuthProperties;
import com.specialweek.auth.exception.AuthException;
import com.specialweek.auth.token.JwtService;
import com.specialweek.auth.token.RefreshTokenStore;
import com.specialweek.auth.token.TokenPair;
import com.specialweek.common.util.RedisConstants;
import com.specialweek.user.api.dto.UserDTO;
import com.specialweek.user.domain.User;
import com.specialweek.user.service.IUserService;
import com.specialweek.user.util.RegexUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

import static com.specialweek.common.util.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final IUserService userService;
    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse register(RegisterRequest request) {
        if (request == null || RegexUtils.isPhoneInvalid(request.phone())) {
            throw new AuthException("手机号格式错误");
        }
        if (!StringUtils.hasText(request.code())) {
            throw new AuthException("验证码不能为空");
        }
        validatePassword(request.password());

        String codeKey = RedisConstants.LOGIN_REGISTER_KEY + request.phone();
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null || !cachedCode.equals(request.code())) {
            throw new AuthException("验证码错误或已过期");
        }
        redisTemplate.delete(codeKey);

        User exist = userService.lambdaQuery()
                .eq(User::getPhone, request.phone())
                .one();
        if (exist != null) {
            throw new AuthException("该手机号已注册");
        }

        User user = new User();
        user.setPhone(request.phone());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(passwordEncoder.encode(request.password()));
        userService.save(user);

        return issueAndStore(user);
    }

    public TokenResponse login(LoginRequest request) {
        if (request == null || RegexUtils.isPhoneInvalid(request.phone())) {
            throw new AuthException("手机号格式错误");
        }

        User user;
        if (StringUtils.hasText(request.password())) {
            user = userService.lambdaQuery()
                    .eq(User::getPhone, request.phone())
                    .one();
            if (user == null || !StringUtils.hasText(user.getPassword())
                    || !passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new AuthException("手机号或密码错误");
            }
        } else if (StringUtils.hasText(request.code())) {
            String codeKey = RedisConstants.LOGIN_CODE_KEY + request.phone();
            String cachedCode = redisTemplate.opsForValue().get(codeKey);
            if (cachedCode == null || !cachedCode.equals(request.code())) {
                throw new AuthException("验证码错误或已过期");
            }
            redisTemplate.delete(codeKey);

            user = userService.lambdaQuery()
                    .eq(User::getPhone, request.phone())
                    .one();
            if (user == null) {
                throw new AuthException("用户未注册");
            }
        } else {
            throw new AuthException("请提供验证码或密码");
        }

        return issueAndStore(user);
    }

    public TokenResponse refresh(String refreshToken) {
        Jwt jwt = decode(refreshToken);
        if (!Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
            throw new AuthException("refresh token 类型错误");
        }

        long userId = jwtService.extractUserId(jwt);
        String tokenId = jwt.getId();
        if (!StringUtils.hasText(tokenId)
                || !refreshTokenStore.consume(userId, tokenId)) {
            throw new AuthException("refresh token 已失效或已被使用");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new AuthException("用户不存在");
        }
        return issueAndStore(user);
    }

    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        try {
            Jwt jwt = jwtService.decodeRefreshToken(refreshToken);
            if (Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
                refreshTokenStore.revoke(jwtService.extractUserId(jwt), jwt.getId());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
        }
    }

    public UserDTO me(long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new AuthException("用户不存在");
        }
        return BeanUtil.copyProperties(user, UserDTO.class);
    }

    public void resetPassword(ResetPasswordRequest request) {
        if (request == null || RegexUtils.isPhoneInvalid(request.phone())) {
            throw new AuthException("手机号格式错误");
        }
        validatePassword(request.newPassword());

        String codeKey = RedisConstants.LOGIN_RESET_KEY + request.phone();
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null || !cachedCode.equals(request.code())) {
            throw new AuthException("手机号或验证码错误");
        }
        redisTemplate.delete(codeKey);

        User user = userService.lambdaQuery()
                .eq(User::getPhone, request.phone())
                .one();
        if (user == null) {
            throw new AuthException("手机号或验证码错误");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userService.updateById(user);
        refreshTokenStore.revokeAll(user.getId());
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new AuthException("密码不能为空");
        }
        String trimmed = password.trim();
        if (trimmed.length() < 8) {
            throw new AuthException("密码长度至少8位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new AuthException("密码需包含字母和数字");
        }
    }

    private TokenResponse issueAndStore(User user) {
        TokenPair pair = jwtService.issueTokenPair(user);
        refreshTokenStore.store(
                user.getId(),
                pair.refreshTokenId(),
                authProperties.getRefreshTokenTtl()
        );
        return new TokenResponse(
                pair.accessToken(),
                pair.accessTokenExpiresAt(),
                pair.refreshToken(),
                pair.refreshTokenExpiresAt()
        );
    }

    private Jwt decode(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new AuthException("refresh token 不能为空");
        }
        try {
            return jwtService.decodeRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException("refresh token 无效或已过期");
        }
    }
}
