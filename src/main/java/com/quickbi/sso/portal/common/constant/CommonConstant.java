package com.quickbi.sso.portal.common.constant;

/**
 * 常量定义
 */
public final class CommonConstant {

    private CommonConstant() {}

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 用户ID请求头 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 用户名请求头 */
    public static final String HEADER_USERNAME = "X-Username";

    /** 默认分页大小 */
    public static final Long DEFAULT_PAGE_SIZE = 10L;

    /** 最大分页大小 */
    public static final Long MAX_PAGE_SIZE = 100L;

    /** 用户状态 - 正常 */
    public static final Integer USER_STATUS_NORMAL = 1;

    /** 用户状态 - 禁用 */
    public static final Integer USER_STATUS_DISABLED = 0;

    /** 缓存前缀 */
    public static final String CACHE_USER_PREFIX = "user:";
    public static final String CACHE_TOKEN_PREFIX = "token:";

    /** 缓存过期时间（秒） */
    public static final Long CACHE_EXPIRE_1H = 3600L;
    public static final Long CACHE_EXPIRE_1D = 86400L;
}
