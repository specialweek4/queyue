package com.specialweek.auth.api;

import com.specialweek.auth.api.dto.LoginRequest;
import com.specialweek.auth.api.dto.RefreshTokenRequest;
import com.specialweek.auth.api.dto.RegisterRequest;
import com.specialweek.auth.api.dto.ResetPasswordRequest;
import com.specialweek.auth.service.AuthService;
import com.specialweek.common.web.Result;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/token/refresh")
    public Result refresh(@RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return Result.ok(authService.me(userId));
    }

    @PostMapping("/register")
    public Result register(@RequestBody RegisterRequest registerRequest){
        return Result.ok(authService.register(registerRequest));
    }

    @PostMapping("/password/reset")
    public Result resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.ok();
    }
}
