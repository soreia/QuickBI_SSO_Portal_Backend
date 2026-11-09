package com.quickbi.sso.portal.common.utils;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前登录用户工具类
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 获取当前登录用户ID
     */
    public static Long getCurrentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前登录用户ID（字符串）
     */
    public static String getCurrentUserIdStr() {
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 判断当前是否已登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 获取当前 Token 值
     */
    public static String getTokenValue() {
        return StpUtil.getTokenValue();
    }
}
