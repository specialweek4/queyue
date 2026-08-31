package com.specialweek.follow.mapper;

import com.specialweek.follow.domain.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.specialweek.user.api.dto.UserDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface FollowMapper extends BaseMapper<Follow> {

    int insertIgnore(@Param("userId") long userId, @Param("followUserId") long followUserId);

    int deleteRelation(@Param("userId") long userId, @Param("followUserId") long followUserId);

    Boolean isFollowing(@Param("userId") long userId, @Param("targetUserId") long targetUserId);

    List<Long> selectFollowedUserIds(@Param("userId") long userId, @Param("ids") List<Long> ids);

    List<UserDTO> selectCommonFollows(@Param("userId") long userId, @Param("targetUserId") long targetUserId);
}
