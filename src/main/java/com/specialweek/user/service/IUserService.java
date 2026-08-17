package com.specialweek.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.specialweek.user.api.dto.LoginFormDTO;
import com.specialweek.common.web.Result;
import com.specialweek.user.domain.User;

import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
