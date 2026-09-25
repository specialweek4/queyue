package com.specialweek.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.specialweek.product.domain.ProductApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductApplicationMapper
        extends BaseMapper<ProductApplication> {

    /**
     * 申请表的 images 在提交申请时已经是正式图片 URL。
     */
    @Update("""
            UPDATE tb_product_application
            SET audit_status = 1,
                audited_by = #{adminId},
                audited_time = NOW(),
                approved_product_id = #{productId},
                reject_reason = NULL
            WHERE id = #{id}
              AND audit_status = 0
            """)
    int approve(
            @Param("id") long id,
            @Param("adminId") long adminId,
            @Param("productId") long productId
    );

    @Update("""
            UPDATE tb_product_application
            SET audit_status = 2,
                audited_by = #{adminId},
                audited_time = NOW(),
                reject_reason = #{reason}
            WHERE id = #{id}
              AND audit_status = 0
            """)
    int reject(
            @Param("id") long id,
            @Param("adminId") long adminId,
            @Param("reason") String reason
    );
}
