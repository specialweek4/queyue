-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: qupingque
-- ------------------------------------------------------
-- Server version	8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `tb_audit_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_audit_record` (
                                   `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                                   `target_type` varchar(40) NOT NULL COMMENT 'SHOP/PRODUCT',
                                   `target_id` bigint unsigned NOT NULL,
                                   `from_status` tinyint unsigned DEFAULT NULL,
                                   `to_status` tinyint unsigned NOT NULL,
                                   `operator_user_id` bigint unsigned NOT NULL,
                                   `reason` varchar(512) DEFAULT NULL,
                                   `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_audit_target` (`target_type`,`target_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_blog`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_blog` (
                           `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                           `user_id` bigint unsigned NOT NULL COMMENT '用户id',
                           `title` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标题',
                           `images` varchar(2048) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图片，最多九张',
                           `liked` int unsigned NOT NULL DEFAULT '0' COMMENT '点赞数检查点',
                           `favorites` int unsigned NOT NULL DEFAULT '0' COMMENT '收藏数检查点',
                           `comments` int unsigned DEFAULT NULL COMMENT '评论数量',
                           `status` tinyint NOT NULL DEFAULT '1' COMMENT '0=草稿 1=已发布',
                           `cover_url` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '封面图 OSS URL',
                           `content_object_key` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '正文 OSS 对象键',
                           `publish_time` timestamp NULL DEFAULT NULL COMMENT '发布时间(可选)',
                           `description` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '摘要/描述，最多50字',
                           `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`) USING BTREE,
                           KEY `idx_blog_public_time` (`status`,`publish_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_blog_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_blog_comments` (
                                    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                                    `user_id` bigint unsigned NOT NULL COMMENT '用户id',
                                    `blog_id` bigint unsigned NOT NULL COMMENT '探店id',
                                    `parent_id` bigint unsigned NOT NULL COMMENT '关联的1级评论id，如果是一级评论，则值为0',
                                    `answer_id` bigint unsigned NOT NULL COMMENT '回复的评论id',
                                    `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回复的内容',
                                    `liked` int unsigned DEFAULT NULL COMMENT '点赞数',
                                    `status` tinyint unsigned DEFAULT NULL COMMENT '状态，0：正常，1：被举报，2：禁止查看',
                                    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_blog_favorite`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_blog_favorite` (
                                    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                                    `user_id` bigint unsigned NOT NULL,
                                    `blog_id` bigint unsigned NOT NULL,
                                    `state` tinyint NOT NULL DEFAULT '1' COMMENT '1=当前收藏，0=已取消',
                                    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_blog_favorite_user_blog` (`user_id`,`blog_id`),
                                    KEY `idx_blog_favorite_user_state_time` (`user_id`,`state`,`update_time`,`id`),
                                    KEY `idx_blog_favorite_blog_state_time` (`blog_id`,`state`,`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_blog_like`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_blog_like` (
                                `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                                `user_id` bigint unsigned NOT NULL,
                                `blog_id` bigint unsigned NOT NULL,
                                `state` tinyint NOT NULL DEFAULT '1' COMMENT '1=当前点赞，0=已取消',
                                `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_blog_like_user_blog` (`user_id`,`blog_id`),
                                KEY `idx_blog_like_user_state_time` (`user_id`,`state`,`update_time`,`id`),
                                KEY `idx_blog_like_blog_state_time` (`blog_id`,`state`,`update_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_follow`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_follow` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                             `user_id` bigint unsigned NOT NULL COMMENT '用户id',
                             `follow_user_id` bigint unsigned NOT NULL COMMENT '关联的用户id',
                             `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             PRIMARY KEY (`id`) USING BTREE,
                             UNIQUE KEY `uk_follow_user_target` (`user_id`,`follow_user_id`),
                             KEY `idx_follow_user_time` (`user_id`,`create_time`,`id`),
                             KEY `idx_follow_target_time` (`follow_user_id`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_product`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_product` (
                              `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                              `shop_id` bigint unsigned NOT NULL,
                              `created_by` bigint unsigned NOT NULL COMMENT '店主用户id',
                              `name` varchar(128) NOT NULL COMMENT '商品名称',
                              `description` varchar(1024) DEFAULT NULL COMMENT '商品描述',
                              `images` varchar(2048) DEFAULT NULL COMMENT '商品图片，逗号分隔',
                              `price` bigint unsigned NOT NULL COMMENT '售价，单位分',
                              `stock` int unsigned NOT NULL DEFAULT '0',
                              `favorites` int unsigned NOT NULL DEFAULT '0' COMMENT '商品收藏数检查点',
                              `status` tinyint unsigned NOT NULL DEFAULT '2' COMMENT '1上架，2下架，3售罄',
                              `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              KEY `idx_product_shop_status` (`shop_id`,`status`),
                              KEY `idx_product_creator` (`created_by`),
                              KEY `idx_product_feed` (`status`,`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_product_application`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_product_application` (
                                          `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                                          `applicant_user_id` bigint unsigned NOT NULL,
                                          `shop_id` bigint unsigned NOT NULL COMMENT '正式店铺id',
                                          `name` varchar(128) NOT NULL,
                                          `description` varchar(1024) DEFAULT NULL,
                                          `images` varchar(2048) DEFAULT NULL,
                                          `price` bigint unsigned NOT NULL COMMENT '售价，单位分',
                                          `stock` int unsigned NOT NULL DEFAULT '0',
                                          `audit_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0待审核，1通过，2拒绝',
                                          `reject_reason` varchar(512) DEFAULT NULL,
                                          `audited_by` bigint unsigned DEFAULT NULL,
                                          `audited_time` timestamp NULL DEFAULT NULL,
                                          `approved_product_id` bigint unsigned DEFAULT NULL,
                                          `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          PRIMARY KEY (`id`),
                                          KEY `idx_product_applicant_status` (`applicant_user_id`,`audit_status`,`create_time`),
                                          KEY `idx_product_application_status` (`audit_status`,`create_time`),
                                          KEY `idx_product_application_shop` (`shop_id`,`audit_status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_product_favorite`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_product_favorite` (
                                       `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                                       `user_id` bigint unsigned NOT NULL,
                                       `product_id` bigint unsigned NOT NULL,
                                       `state` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '1=当前收藏，0=已取消',
                                       `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_product_favorite_user_product` (`user_id`,`product_id`),
                                       KEY `idx_product_favorite_user_state_time` (`user_id`,`state`,`update_time`,`id`),
                                       KEY `idx_product_favorite_product_state` (`product_id`,`state`,`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_seckill_voucher`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_seckill_voucher` (
                                      `voucher_id` bigint unsigned NOT NULL COMMENT '关联的优惠券的id',
                                      `stock` int NOT NULL COMMENT '库存',
                                      `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `begin_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' COMMENT '生效时间',
                                      `end_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' COMMENT '失效时间',
                                      `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`voucher_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='秒杀优惠券表，与优惠券是一对一关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_shop`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_shop` (
                           `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                           `owner_user_id` bigint unsigned DEFAULT NULL COMMENT '店主用户id',
                           `business_status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '1营业中，2暂停营业，3永久关闭',
                           `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺名称',
                           `type_id` bigint unsigned NOT NULL COMMENT '商铺类型的id',
                           `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺图片，多个图片以'',''隔开',
                           `area` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商圈，例如陆家嘴',
                           `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '地址',
                           `x` decimal(10,7) NOT NULL COMMENT '经度',
                           `y` decimal(10,7) NOT NULL COMMENT '纬度',
                           `avg_price` bigint unsigned DEFAULT NULL COMMENT '均价，取整数',
                           `sold` int(10) unsigned zerofill NOT NULL COMMENT '销量',
                           `comments` int(10) unsigned zerofill NOT NULL COMMENT '评论数量',
                           `score` int(2) unsigned zerofill NOT NULL COMMENT '评分，1~5分，乘10保存，避免小数',
                           `open_hours` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '营业时间，例如 10:00-22:00',
                           `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE KEY `uk_shop_owner` (`owner_user_id`),
                           KEY `foreign_key_type` (`type_id`) USING BTREE,
                           KEY `idx_shop_owner` (`owner_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_shop_application`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_shop_application` (
                                       `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                                       `apply_type` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0新建商铺申请，1商铺资料修改申请',
                                       `applicant_user_id` bigint unsigned NOT NULL,
                                       `target_shop_id` bigint unsigned DEFAULT NULL COMMENT '修改申请对应的正式店铺id，新建申请为NULL',
                                       `name` varchar(128) NOT NULL,
                                       `type_id` bigint unsigned NOT NULL,
                                       `images` varchar(1024) NOT NULL,
                                       `area` varchar(128) DEFAULT NULL,
                                       `address` varchar(255) NOT NULL,
                                       `x` decimal(10,7) DEFAULT NULL COMMENT '经度，由审核人员补齐',
                                       `y` decimal(10,7) DEFAULT NULL COMMENT '纬度，由审核人员补齐',
                                       `avg_price` bigint unsigned DEFAULT NULL,
                                       `open_hours` varchar(32) DEFAULT NULL,
                                       `audit_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0待审核，1通过，2拒绝',
                                       `reject_reason` varchar(512) DEFAULT NULL,
                                       `audited_by` bigint unsigned DEFAULT NULL,
                                       `audited_time` timestamp NULL DEFAULT NULL,
                                       `approved_shop_id` bigint unsigned DEFAULT NULL,
                                       `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       PRIMARY KEY (`id`),
                                       KEY `idx_shop_applicant_status` (`applicant_user_id`,`audit_status`,`create_time`),
                                       KEY `idx_shop_application_status` (`audit_status`,`create_time`),
                                       KEY `idx_shop_apply_target` (`target_shop_id`,`audit_status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_shop_type`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_shop_type` (
                                `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                                `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型名称',
                                `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
                                `sort` int unsigned DEFAULT NULL COMMENT '顺序',
                                `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_sign`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_sign` (
                           `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                           `user_id` bigint unsigned NOT NULL COMMENT '用户id',
                           `year` year NOT NULL COMMENT '签到的年',
                           `month` tinyint NOT NULL COMMENT '签到的月',
                           `date` date NOT NULL COMMENT '签到的日期',
                           `is_backup` tinyint unsigned DEFAULT NULL COMMENT '是否补签',
                           PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_user`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_user` (
                           `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                           `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号码',
                           `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码，加密存储',
                           `role` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '角色：0普通用户，1商家，9管理员',
                           `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '昵称，默认是用户id',
                           `avatar` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '用户头像',
                           `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `email` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
                           `bio` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '个人简介',
                           `qy_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '知光号',
                           `gender` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '性别',
                           `birthday` date DEFAULT NULL COMMENT '生日',
                           `school` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '学校',
                           `tags_json` json DEFAULT NULL COMMENT '用户标签',
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE KEY `uk_users_phone` (`phone`),
                           UNIQUE KEY `uk_users_email` (`email`),
                           UNIQUE KEY `uk_users_zg_id` (`qy_id`),
                           KEY `idx_user_role` (`role`)
) ENGINE=InnoDB AUTO_INCREMENT=1014 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_voucher`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_voucher` (
                              `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                              `shop_id` bigint unsigned DEFAULT NULL COMMENT '商铺id',
                              `created_by` bigint unsigned DEFAULT NULL COMMENT '创建优惠券的店主用户id',
                              `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '代金券标题',
                              `sub_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '副标题',
                              `rules` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '使用规则',
                              `pay_value` bigint unsigned NOT NULL COMMENT '支付金额，单位是分。例如200代表2元',
                              `actual_value` bigint NOT NULL COMMENT '抵扣金额，单位是分。例如200代表2元',
                              `type` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0,普通券；1,秒杀券',
                              `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '1,上架; 2,下架; 3,过期',
                              `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              `product_id` bigint unsigned DEFAULT NULL COMMENT '可选，适用商品id',
                              PRIMARY KEY (`id`) USING BTREE,
                              KEY `idx_voucher_creator` (`created_by`),
                              KEY `idx_voucher_product` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_voucher_order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_voucher_order` (
                                    `id` bigint NOT NULL COMMENT '主键',
                                    `user_id` bigint unsigned NOT NULL COMMENT '下单的用户id',
                                    `voucher_id` bigint unsigned NOT NULL COMMENT '购买的代金券id',
                                    `pay_type` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
                                    `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款',
                                    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
                                    `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
                                    `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
                                    `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
                                    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-21 20:01:15
