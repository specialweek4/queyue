package com.specialweek.user.mapper;

import com.specialweek.user.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface UserMapper extends BaseMapper<User> {
    User findById(@Param("id") Long id);

    void updateProfile(User user);

    int countFollowee(@Param("userId") long userId);

    int countFans(@Param("userId") long userId);
}
