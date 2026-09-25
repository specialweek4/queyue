package com.specialweek.counter.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BlogBehaviorRelationMapper {
    int restoreLike(@Param("userId") long userId, @Param("blogId") long blogId);
    int insertLikeIfAbsent(@Param("userId") long userId, @Param("blogId") long blogId);
    int cancelLike(@Param("userId") long userId, @Param("blogId") long blogId);
    int restoreFavorite(@Param("userId") long userId, @Param("blogId") long blogId);
    int insertFavoriteIfAbsent(@Param("userId") long userId, @Param("blogId") long blogId);
    int cancelFavorite(@Param("userId") long userId, @Param("blogId") long blogId);

    List<Long> selectLikedBlogIds(@Param("userId") long userId,
                                  @Param("offset") int offset,
                                  @Param("size") int size);
    List<Long> selectFavoriteBlogIds(@Param("userId") long userId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);
}
