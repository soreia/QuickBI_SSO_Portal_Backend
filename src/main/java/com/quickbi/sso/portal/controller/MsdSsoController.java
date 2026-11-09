package com.quickbi.sso.portal.controller;

import com.quickbi.sso.portal.common.result.Result;
import com.quickbi.sso.portal.entity.dto.LoginVO;
import com.quickbi.sso.portal.entity.dto.MsdExchangeDTO;
import com.quickbi.sso.portal.service.MsdOidcService;
import com.quickbi.sso.portal.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * MSD Entra ID 单点登录入口。
 *
 * 前端只需要两个动作：
 * 1. 登录按钮跳到 GET /auth/msd/authorize（后端 302 到 Entra ID）
 * 2. 回调页把 code + state POST 到 /auth/msd/exchange，换回 Sa-Token
 */
@Slf4j
@Tag(name = "MSD 单点登录", description = "Entra ID OIDC 接入")
@RestController
@RequestMapping("/auth/msd")
@RequiredArgsConstructor
public class MsdSsoController {

    private final MsdOidcService msdOidcService;
    private final SysUserService sysUserService;

    @Operation(summary = "跳转 Entra ID 登录")
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        String url = msdOidcService.buildAuthorizeUrl();
        log.info("发起 MSD SSO 登录，跳转至 {}", url);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @Operation(summary = "授权码换取登录态")
    @PostMapping("/exchange")
    public Result<LoginVO> exchange(@RequestBody MsdExchangeDTO dto) {
        MsdOidcService.MsdIdentity identity = msdOidcService.exchange(dto.getCode(), dto.getState());
        LoginVO vo = sysUserService.loginByIsid(identity.getIsid(), identity.getDisplayName(), identity.getEmail());
        return Result.success("登录成功", vo);
    }
}
