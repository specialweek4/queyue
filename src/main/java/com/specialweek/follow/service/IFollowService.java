package com.specialweek.follow.service;

import com.specialweek.follow.domain.Follow;
import com.specialweek.follow.dto.FollowActionResponse;
import com.baomidou.mybatisplus.extension.service.IService;
import com.specialweek.user.api.dto.UserDTO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface IFollowService extends IService<Follow> {

    FollowActionResponse follow(long userId, long targetUserId);

    FollowActionResponse unfollow(long userId, long targetUserId);

    boolean isFollowed(long userId, long targetUserId);

    List<UserDTO> commonFollows(long userId, long targetUserId);
}
