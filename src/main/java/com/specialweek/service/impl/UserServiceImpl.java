package com.specialweek.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.entity.User;
import com.specialweek.mapper.UserMapper;
import com.specialweek.service.IUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
