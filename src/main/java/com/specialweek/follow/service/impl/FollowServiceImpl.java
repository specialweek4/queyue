package com.specialweek.follow.service.impl;

import com.specialweek.follow.domain.Follow;
import com.specialweek.follow.dto.FollowActionResponse;
import com.specialweek.follow.mapper.FollowMapper;
import com.specialweek.follow.service.FollowStateService;
import com.specialweek.follow.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.user.api.dto.UserDTO;
import com.specialweek.user.mapper.UserInfoMapper;
import com.specialweek.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final FollowStateService followStateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowActionResponse follow(long userId, long targetUserId) {
        requireExistingTarget(userId, targetUserId);
        int inserted = baseMapper.insertIgnore(userId, targetUserId);
        if (inserted == 1) {
            userInfoMapper.ensureRows(userId, targetUserId);
            userInfoMapper.incrementFollowee(userId);
            userInfoMapper.incrementFans(targetUserId);
            afterCommitUpdateFollowState(userId, targetUserId, true);
        }
        return new FollowActionResponse(targetUserId, true, inserted == 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowActionResponse unfollow(long userId, long targetUserId) {
        requireExistingTarget(userId, targetUserId);
        int deleted = baseMapper.deleteRelation(userId, targetUserId);
        if (deleted == 1) {
            userInfoMapper.decrementFollowee(userId);
            userInfoMapper.decrementFans(targetUserId);
            afterCommitUpdateFollowState(userId, targetUserId, false);
        }
        return new FollowActionResponse(targetUserId, false, deleted == 1);
    }

    @Override
    public boolean isFollowed(long userId, long targetUserId) {
        return followStateService.isFollowed(userId, targetUserId);
    }

    @Override
    public List<UserDTO> commonFollows(long userId, long targetUserId) {
        if (targetUserId <= 0 || userMapper.selectById(targetUserId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return baseMapper.selectCommonFollows(userId, targetUserId);
    }

    private void requireExistingTarget(long userId, long targetUserId) {
        if (targetUserId <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (userId == targetUserId) {
            throw new IllegalArgumentException("不能关注自己");
        }
        if (userMapper.selectById(targetUserId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    private void afterCommitUpdateFollowState(long userId, long targetUserId, boolean following) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    followStateService.setFollowState(userId, targetUserId, following);
                }
            });
        } else {
            followStateService.setFollowState(userId, targetUserId, following);
        }
    }
}
