package com.quickbi.sso.portal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.quickbi.sso.portal.entity.SysUser;
import com.quickbi.sso.portal.entity.dto.LoginDTO;
import com.quickbi.sso.portal.entity.dto.LoginVO;

/**
 * 用户 Service
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户登录
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 根据用户名查询用户
     */
    SysUser getByUsername(String username);

    /**
     * 企业 SSO 登录：按 ISID 查找用户，不存在则自动建档，然后签发 Sa-Token。
     *
     * @param isid        MSD 员工标识
     * @param displayName 显示名
     * @param email       邮箱
     */
    LoginVO loginByIsid(String isid, String displayName, String email);

    /**
     * 用户登出
     */
    void logout();
}
