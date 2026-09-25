package com.specialweek.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.specialweek.product.domain.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import com.specialweek.product.model.ProductFeedRow;
import com.specialweek.product.model.ProductDetailRow;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 查询指定商铺的上架商品列表。
     * @param shopId
     * @return
     */
    @Select("""
            SELECT id, shop_id, name, description, images, price, stock
            FROM tb_product
            WHERE shop_id = #{shopId} AND status = 1
            ORDER BY create_time DESC
            """)
    List<Product> queryPublicByShop(@Param("shopId") long shopId);
    /**
     * 分页查询公开商品及商铺和发布者基础信息。
     * @param limit
     * @param offset
     * @return
     */
    List<ProductFeedRow> selectPublicFeed(@Param("limit") int limit,
                                        @Param("offset") int offset);

    /**
     * 批量查询指定 ID 的公开商品基础信息。
     * @param productIds
     * @return
     */
    List<ProductFeedRow> selectFeedByIds(@Param("productIds") List<Long> productIds);

    /**
     * 联表查询公开商品及所属商铺详情。
     * @param productId
     * @return
     */
    ProductDetailRow selectPublicDetail(@Param("productId") long productId);

    /**
     * 保存商品收藏计数检查点并保持商品排序时间不变。
     * @param productId
     * @param favoriteCount
     * @return
     */
    int updateFavoriteCheckpoint(@Param("productId") long productId,
                                 @Param("favoriteCount") int favoriteCount);
}
