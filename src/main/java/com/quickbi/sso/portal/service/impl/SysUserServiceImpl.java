package com.quickbi.sso.portal.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.quickbi.sso.portal.common.exception.BusinessException;
import com.quickbi.sso.portal.common.result.ResultCode;
import com.quickbi.sso.portal.entity.SysUser;
import com.quickbi.sso.portal.entity.dto.LoginDTO;
import com.quickbi.sso.portal.entity.dto.LoginVO;
import com.quickbi.sso.portal.mapper.SysUserMapper;
import com.quickbi.sso.portal.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        SysUser user = getByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // TODO: 密码校验（BCrypt）
        // if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
        //     throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        // }

        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(StpUtil.getTokenValue());
        loginVO.setExpire(StpUtil.getTokenTimeout());
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setNickname(user.getNickname());
        loginVO.setAvatar(user.getAvatar());

        log.info("用户登录成功: {}", user.getUsername());
        return loginVO;
    }

    @Override
    public SysUser getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
    }

    @Override
    public LoginVO loginByIsid(String isid, String displayName, String email) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsid, isid)
                .last("limit 1"));

        if (user == null) {
            // 首次通过企业 SSO 进入的用户自动建档，密码留空（不走账号密码登录）
            user = new SysUser();
            user.setIsid(isid);
            user.setUsername(isid);
            user.setPassword("");
            user.setNickname(displayName == null || displayName.isBlank() ? isid : displayName);
            user.setEmail(email);
            user.setStatus(1);
            user.setRole("USER");
            save(user);
            log.info("SSO 首次登录，自动建档 isid={} userId={}", isid, user.getId());
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        StpUtil.login(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setExpire(StpUtil.getTokenTimeout());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());

        log.info("SSO 登录成功 isid={} userId={}", isid, user.getId());
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
