package com.quickbi.sso.portal.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.quickbi.sso.portal.common.result.Result;
import com.quickbi.sso.portal.entity.SysUser;
import com.quickbi.sso.portal.entity.dto.LoginDTO;
import com.quickbi.sso.portal.entity.dto.LoginVO;
import com.quickbi.sso.portal.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "登录、登出、用户信息")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginVO loginVO = sysUserService.login(loginDTO);
        return Result.success("登录成功", loginVO);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        sysUserService.logout();
        return Result.success("登出成功", null);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/userinfo")
    public Result<SysUser> getUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserService.getById(userId);
        user.setPassword(null);
        return Result.success(user);
    }
}
