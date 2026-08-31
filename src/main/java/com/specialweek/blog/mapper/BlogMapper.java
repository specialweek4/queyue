package com.specialweek.blog.mapper;

import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.model.BlogFeedRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface BlogMapper extends BaseMapper<Blog> {

    int updateCounterCheckpoint(@Param("blogId") long blogId,
                                @Param("likeCount") int likeCount,
                                @Param("favoriteCount") int favoriteCount);

    List<Blog> selectFeedOfFollow(@Param("userId") long userId,
                                  @Param("maxTime") LocalDateTime maxTime,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    List<BlogFeedRow> selectHotFeed(@Param("limit") int limit,
                                    @Param("offset") int offset);
}
