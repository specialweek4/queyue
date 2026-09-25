package com.specialweek.user.api;

import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.user.api.dto.ProfileRequest;
import com.specialweek.user.domain.User;
import com.specialweek.user.service.IUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Autowired
    private OssStorageService ossStorageService;

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone,
                           @RequestParam(value = "scene", defaultValue = "login") String scene,
                           HttpSession session) {
        return userService.sendCode(phone, scene, session);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        User info = userService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        return Result.ok(userService.profile(userId));
    }

    @PutMapping("/profile")
    public Result updateProfile(@AuthenticationPrincipal Jwt jwt,
                                @RequestBody ProfileRequest request) {
        User user = new User();
        user.setId(request.getId());
        user.setBio(request.getBio());
        user.setBirthday(request.getBirthday());
        user.setGender(request.getGender());
        user.setSchool(request.getSchool());
        user.setTagsJson(request.getTagsJson());
        user.setNickName(request.getNickName());

        return Result.ok(userService.updateProfile(
                Long.parseLong(jwt.getSubject()),
                user
        ));
    }

    @PutMapping("/sign")
    public Result sign(@AuthenticationPrincipal Jwt jwt) {
        return userService.sign(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/sign/count")
    public Result signCount(@AuthenticationPrincipal Jwt jwt) {
        return userService.signCount(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/avatar")
    public Result uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                               @RequestPart("file") MultipartFile file){
        long userId = Long.parseLong(jwt.getSubject());
        String url =ossStorageService.uploadAvatar(userId, file);
        return Result.ok(userService.updateAvatar(userId,url));
    }
}
