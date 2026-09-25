package com.specialweek.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.specialweek.shop.domain.ShopApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface ShopApplicationMapper
        extends BaseMapper<ShopApplication> {

    /**
     * 申请表的 images 在提交申请时已经是正式图片 URL。
     */
    @Update("""
            UPDATE tb_shop_application
            SET audit_status = 1,
                x = #{x},
                y = #{y},
                audited_by = #{adminId},
                audited_time = NOW(),
                approved_shop_id = #{shopId},
                reject_reason = NULL
            WHERE id = #{id}
              AND audit_status = 0
            """)
    int approve(
            @Param("id") long id,
            @Param("adminId") long adminId,
            @Param("shopId") long shopId,
            @Param("x") BigDecimal x,
            @Param("y") BigDecimal y
    );

    @Update("""
            UPDATE tb_shop_application
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
