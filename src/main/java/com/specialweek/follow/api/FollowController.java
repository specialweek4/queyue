package com.specialweek.follow.api;


import com.specialweek.common.web.Result;
import com.specialweek.follow.dto.FollowActionResponse;
import com.specialweek.follow.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @GetMapping("/or/not/{id}")
    public Result orNot(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return Result.ok(followService.isFollowed(userId, id));
    }

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,
                         @PathVariable("isFollow") Boolean isFollow,
                         @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        FollowActionResponse response = Boolean.TRUE.equals(isFollow)
                ? followService.follow(userId, id)
                : followService.unfollow(userId, id);
        return Result.ok(response);
    }

    @GetMapping("/common/{id}")
    public Result common(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return Result.ok(followService.commonFollows(userId, id));
    }
}
