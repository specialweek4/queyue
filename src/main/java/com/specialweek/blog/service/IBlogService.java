package com.specialweek.blog.service;

import com.specialweek.blog.domain.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.specialweek.common.web.Result;
import com.specialweek.common.web.ScrollResult;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface IBlogService extends IService<Blog> {

    Result delete(long userid, long id);

    ScrollResult queryFeedOfFollow(long userId, long lastIdMillis, int offset);

    List<Blog> deletelist(long userId, Integer current, int maxPageSize);

    Result blogDeleteForever(Long blogId, long userId);

    Result revive(Long blogId, long userId);
}
