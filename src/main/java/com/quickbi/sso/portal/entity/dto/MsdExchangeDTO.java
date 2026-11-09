package com.quickbi.sso.portal.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 前端回调页回传的授权码交换请求。
 */
@Data
@Schema(description = "OIDC 授权码交换请求")
public class MsdExchangeDTO {

    @Schema(description = "Entra ID 回传的授权码")
    private String code;

    @Schema(description = "发起登录时后端下发的一次性 state")
    private String state;
}
