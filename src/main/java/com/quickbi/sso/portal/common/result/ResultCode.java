package com.quickbi.sso.portal.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 业务错误 6xx
    USER_NOT_FOUND(601, "用户不存在"),
    USER_PASSWORD_ERROR(602, "用户名或密码错误"),
    USER_DISABLED(603, "用户已被禁用"),
    USER_ALREADY_EXISTS(604, "用户已存在"),
    TOKEN_INVALID(610, "Token无效"),
    TOKEN_EXPIRED(611, "Token已过期"),
    SSO_LOGIN_FAILED(620, "SSO登录失败"),
    SSO_TOKEN_INVALID(621, "SSO Token无效"),
    PERMISSION_DENIED(630, "权限不足");

    private final Integer code;
    private final String message;
}
