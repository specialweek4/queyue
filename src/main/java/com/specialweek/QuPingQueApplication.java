package com.specialweek;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author specialweek
 * @since 2026-08-15
 */
@MapperScan({"com.specialweek.user.mapper", "com.specialweek.shop.mapper", "com.specialweek.blog.mapper", "com.specialweek.follow.mapper", "com.specialweek.voucher.mapper"})
@SpringBootApplication
public class QuPingQueApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuPingQueApplication.class, args);
    }

}
