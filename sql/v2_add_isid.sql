-- =============================================
-- 增量脚本：为已存在的库补上 ISID 字段
-- 仅当 sys_user 表已建好、且尚未执行过 init.sql 新版时使用
-- =============================================

USE `quickbi_sso_portal`;

ALTER TABLE `sys_user`
    ADD COLUMN `isid` VARCHAR(64) DEFAULT NULL COMMENT 'MSD 员工标识 ISID（企业 SSO 唯一身份）' AFTER `username`;

ALTER TABLE `sys_user`
    ADD UNIQUE KEY `uk_isid` (`isid`);
