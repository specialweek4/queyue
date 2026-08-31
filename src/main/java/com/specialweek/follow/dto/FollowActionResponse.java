package com.specialweek.follow.dto;

public record FollowActionResponse(long targetUserId, boolean following, boolean changed) {
}
