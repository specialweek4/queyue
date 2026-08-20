package com.specialweek.user.api;

import com.specialweek.common.web.Result;
import com.specialweek.user.domain.UserInfo;
import com.specialweek.user.service.IUserInfoService;
import com.specialweek.user.service.IUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone,
                           @RequestParam(value = "scene", defaultValue = "login") String scene,
                           HttpSession session) {
        return userService.sendCode(phone, scene, session);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    @PutMapping("/sign")
    public Result sign(@AuthenticationPrincipal Jwt jwt) {
        return userService.sign(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/sign/count")
    public Result signCount(@AuthenticationPrincipal Jwt jwt) {
        return userService.signCount(Long.parseLong(jwt.getSubject()));
    }
}
