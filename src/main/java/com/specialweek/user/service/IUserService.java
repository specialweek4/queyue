package com.specialweek.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.specialweek.common.web.Result;
import com.specialweek.user.domain.User;

import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, String scene, HttpSession session);

    Result sign(Long userId);

    Result signCount(Long userId);
}
