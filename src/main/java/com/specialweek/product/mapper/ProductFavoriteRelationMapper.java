package com.specialweek.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductFavoriteRelationMapper {
    /**
     * 恢复用户已经取消的商品收藏关系。
     * @param userId
     * @param productId
     * @return
     */
    int restore(@Param("userId") long userId,
                @Param("productId") long productId);

    /**
     * 在商品收藏关系不存在时插入记录。
     * @param userId
     * @param productId
     * @return
     */
    int insertIfAbsent(@Param("userId") long userId,
                       @Param("productId") long productId);

    /**
     * 取消用户当前有效的商品收藏关系。
     * @param userId
     * @param productId
     * @return
     */
    int cancel(@Param("userId") long userId,
               @Param("productId") long productId);

    /**
     * 按收藏时间分页查询仍然公开的商品 ID。
     * @param userId
     * @param offset
     * @param size
     * @return
     */
    List<Long> selectFavoriteProductIds(@Param("userId") long userId,
                                        @Param("offset") int offset,
                                        @Param("size") int size);
}
