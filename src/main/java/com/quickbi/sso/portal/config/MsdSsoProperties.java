package com.quickbi.sso.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MSD Entra ID（OIDC）接入参数。
 *
 * 这些值来自 Azure 门户中 MSD 租户的应用注册（App registration）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "msd.sso")
public class MsdSsoProperties {

    /** Entra ID 租户标识，可用租户 ID（GUID）或 xxxx.onmicrosoft.com 域名 */
    private String tenantId;

    /** 应用注册的 Application (client) ID */
    private String clientId;

    /** 客户端密钥，纯 PKCE 公共客户端可留空 */
    private String clientSecret;

    /** 登录成功后前端接收 code 的地址，必须与应用注册里的重定向 URI 完全一致 */
    private String redirectUri;

    /** 申请的权限范围 */
    private String scope = "openid profile email offline_access";

    /** ISID 在 id_token 中的 claim 名称，需在应用注册里配置为“可选声明/令牌配置” */
    private String isidClaim = "isid";

    /** 显示名 claim，一般就是 name */
    private String displayNameClaim = "name";

    /** 邮箱 claim */
    private String emailClaim = "email";

    /** 本地无 Azure 环境时用于联调的模拟模式，生产必须关闭 */
    private boolean mockEnabled = false;

    /** 登录成功后的前端落地页 */
    private String successRedirect = "/MyReports";
}
