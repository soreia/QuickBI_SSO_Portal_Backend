-- =============================================
-- QuickBI SSO Portal 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS `quickbi_sso_portal` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `quickbi_sso_portal`;

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `isid`        VARCHAR(64)  DEFAULT NULL COMMENT 'MSD 员工标识 ISID（企业 SSO 唯一身份）',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '用户昵称',
    `avatar`      VARCHAR(256) DEFAULT NULL COMMENT '用户头像',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
    `role`        VARCHAR(32)  DEFAULT NULL COMMENT '角色',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_isid` (`isid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ----------------------------
-- 初始管理员账号（密码: admin123，实际项目需使用 BCrypt 加密）
-- ----------------------------
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `status`, `role`)
VALUES ('admin', 'admin123', '系统管理员', 'admin@quickbi.com', 1, 'ADMIN');
