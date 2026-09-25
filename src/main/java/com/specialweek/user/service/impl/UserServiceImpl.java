package com.specialweek.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.common.except.BusinessException;
import com.specialweek.common.util.RedisConstants;
import com.specialweek.common.web.Result;
import com.specialweek.user.api.dto.SignCountResponse;
import com.specialweek.user.api.dto.UserDTO;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerUser(User user) {
        save(user);
    }

    @Override
    public UserDTO updateAvatar(long userId, String avatarUrl) {
        User current = userMapper.findById(userId);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }

        // 仅更新头像字段
        User patch = new User();
        patch.setId(userId);
        patch.setAvatar(avatarUrl);
        userMapper.updateProfile(patch);

        // 更新后回读，保证返回最新头像地址
        User updated = userMapper.findById(userId);
        return toResponseWithCounts(updated);
    }

    @Override
    public UserDTO profile(long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            return null;
        }
        return toResponseWithCounts(user);
    }

    @Override
    public UserDTO updateProfile(long userId, User patch) {
        User current = userMapper.findById(userId);
        if (current == null) {
            throw new BusinessException("用户不存在");
        }
        patch.setId(userId);
        userMapper.updateProfile(patch);
        return profile(userId);
    }

    private UserDTO toResponseWithCounts(User user){
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setAvatar(user.getAvatar());
        dto.setRole(user.getRole());
        dto.setEmail(user.getEmail());
        dto.setBio(user.getBio());
        dto.setQyId(user.getQyId());
        dto.setGender(user.getGender());
        dto.setBirthday(user.getBirthday());
        dto.setSchool(user.getSchool());
        dto.setTagsJson(user.getTagsJson());
        dto.setFollowee(userMapper.countFollowee(user.getId()));
        dto.setFans(userMapper.countFans(user.getId()));
        return dto;
    }

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
