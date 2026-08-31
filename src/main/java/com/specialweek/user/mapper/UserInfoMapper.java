package com.specialweek.user.mapper;

import com.specialweek.user.domain.UserInfo;
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
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    int ensureRows(@Param("userId") long userId, @Param("targetUserId") long targetUserId);

    int incrementFollowee(@Param("userId") long userId);

    int incrementFans(@Param("userId") long userId);

    int decrementFollowee(@Param("userId") long userId);

    int decrementFans(@Param("userId") long userId);
}
