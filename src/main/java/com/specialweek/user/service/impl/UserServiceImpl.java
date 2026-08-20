package com.specialweek.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.common.util.RedisConstants;
import com.specialweek.common.web.Result;
import com.specialweek.user.api.dto.SignCountResponse;
import com.specialweek.user.domain.User;
import com.specialweek.user.mapper.UserMapper;
import com.specialweek.user.service.IUserService;
import com.specialweek.user.util.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, String scene, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        String code = RandomUtil.randomNumbers(6);

        String keyPrefix = switch (scene) {
            case "register" -> RedisConstants.LOGIN_REGISTER_KEY;
            case "reset" -> RedisConstants.LOGIN_RESET_KEY;
            default -> RedisConstants.LOGIN_CODE_KEY;
        };
        stringRedisTemplate.opsForValue().set(keyPrefix + phone, code, 2, TimeUnit.MINUTES);
        log.debug("发送验证码成功:" + code);
        return Result.ok();
    }

    @Override
    public Result sign(Long userId) {
        LocalDateTime now = LocalDateTime.now();

        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;

        int dayOfMonth = now.getDayOfMonth();

        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result signCount(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        Long num = (result == null || result.isEmpty()) ? 0L : result.get(0);
        if (num == null) {
            num = 0L;
        }
        boolean today = (num & 1) == 1;
        long bits = today ? num : (num >>> 1);
        int streak = 0;
        while ((bits & 1) == 1) {
            streak++;
            bits >>>= 1;
        }
        Long monthCount = stringRedisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.bitCount(key.getBytes(StandardCharsets.UTF_8))
        );
        int monthDays = monthCount == null ? 0 : monthCount.intValue();
        return Result.ok(new SignCountResponse(today, streak, monthDays));
    }
}
