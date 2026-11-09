package com.quickbi.sso.portal.service;

import com.quickbi.sso.portal.common.exception.BusinessException;
import com.quickbi.sso.portal.common.result.ResultCode;
import com.quickbi.sso.portal.config.MsdSsoProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 与 MSD Entra ID 的 OIDC 交互：
 * 1. 生成授权地址（带 PKCE + state + nonce）
 * 2. 用授权码换 id_token
 * 3. 用 JWKS 验签并取出 ISID 等身份信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MsdOidcService {

    private final MsdSsoProperties props;

    private final RestClient restClient = RestClient.create();

    /** state -> 待完成的登录上下文，一次性使用 */
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();

    /** JWKS 缓存 */
    private volatile Map<String, PublicKey> keyCache = Map.of();
    private volatile long keyCacheAt = 0L;
    private static final long KEY_TTL_MILLIS = 10 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 登录上下文有效期 */
    private static final long PENDING_TTL_MILLIS = 10 * 60 * 1000L;

    @Data
    public static class PendingLogin {
        private String verifier;
        private String nonce;
        private long expireAt;
    }

    /** 从 id_token 中提取出来的身份信息 */
    @Data
    public static class MsdIdentity {
        private String isid;
        private String displayName;
        private String email;
        private String objectId;
        private String tenantId;
        private Map<String, Object> rawClaims = new HashMap<>();
    }

    // ---------------------------------------------------------------- 授权地址

    /**
     * 生成 Entra ID 授权地址，前端直接跳过去。
     */
    public String buildAuthorizeUrl() {
        String state = randomUrlSafe(32);
        String nonce = randomUrlSafe(32);
        String verifier = randomUrlSafe(32);
        String challenge = sha256Base64Url(verifier);

        PendingLogin p = new PendingLogin();
        p.setVerifier(verifier);
        p.setNonce(nonce);
        p.setExpireAt(System.currentTimeMillis() + PENDING_TTL_MILLIS);
        pending.put(state, p);
        cleanExpired();

        if (props.isMockEnabled()) {
            // 联调模式：直接跳回前端回调页，附假 code
            log.warn("MSD SSO 处于 mock 模式，仅用于本地联调");
            return props.getRedirectUri() + "?code=mock-code&state=" + state;
        }

        String base = authority() + "/oauth2/v2.0/authorize";
        return base
                + "?client_id=" + enc(props.getClientId())
                + "&response_type=code"
                + "&redirect_uri=" + enc(props.getRedirectUri())
                + "&response_mode=query"
                + "&scope=" + enc(props.getScope())
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce)
                + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256";
    }

    // ---------------------------------------------------------------- 换取身份

    /**
     * 用授权码换取 id_token，验签后返回 MSD 身份信息。
     */
    public MsdIdentity exchange(String code, String state) {
        PendingLogin p = pending.remove(state);
        if (p == null || p.getExpireAt() < System.currentTimeMillis()) {
            throw new BusinessException(ResultCode.SSO_LOGIN_FAILED, "登录会话已失效，请重新登录");
        }

        if (props.isMockEnabled()) {
            return mockIdentity(code);
        }

        String idToken = requestIdToken(code, p.getVerifier());
        Claims claims = verifyIdToken(idToken);

        Object nonce = claims.get("nonce");
        if (nonce != null && !p.getNonce().equals(String.valueOf(nonce))) {
            throw new BusinessException(ResultCode.SSO_TOKEN_INVALID, "nonce 校验不通过");
        }

        MsdIdentity id = new MsdIdentity();
        id.setIsid(asString(claims.get(props.getIsidClaim())));
        id.setDisplayName(asString(claims.get(props.getDisplayNameClaim())));
        id.setEmail(asString(claims.get(props.getEmailClaim())));
        id.setObjectId(asString(claims.get("oid")));
        id.setTenantId(asString(claims.get("tid")));
        id.setRawClaims(new LinkedHashMap<>(claims));

        if (id.getIsid() == null || id.getIsid().isBlank()) {
            throw new BusinessException(ResultCode.SSO_TOKEN_INVALID,
                    "id_token 中缺少 " + props.getIsidClaim() + " 声明，请在应用注册中配置该可选声明");
        }
        log.info("MSD 身份解析成功 isid={} name={}", id.getIsid(), id.getDisplayName());
        return id;
    }

    /** 调用 token 端点换取 id_token */
    private String requestIdToken(String code, String verifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.getClientId());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.getRedirectUri());
        form.add("code_verifier", verifier);
        form.add("scope", props.getScope());
        if (props.getClientSecret() != null && !props.getClientSecret().isBlank()) {
            form.add("client_secret", props.getClientSecret());
        }

        try {
            Map<String, Object> resp = restClient.post()
                    .uri(authority() + "/oauth2/v2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (resp == null || resp.get("id_token") == null) {
                throw new BusinessException(ResultCode.SSO_TOKEN_INVALID, "token 端点未返回 id_token");
            }
            return String.valueOf(resp.get("id_token"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("换取 id_token 失败", e);
            throw new BusinessException(ResultCode.SSO_LOGIN_FAILED, "与 Entra ID 通信失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 验签

    private Claims verifyIdToken(String idToken) {
        String kid = readKid(idToken);
        PublicKey key = publicKeys().get(kid);
        if (key == null) {
            // kid 未命中，强制刷新一次 JWKS
            keyCacheAt = 0L;
            key = publicKeys().get(kid);
        }
        if (key == null) {
            throw new BusinessException(ResultCode.SSO_TOKEN_INVALID, "找不到签名公钥 kid=" + kid);
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(idToken);
            return jws.getPayload();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SSO_TOKEN_INVALID, "id_token 验签失败：" + e.getMessage());
        }
    }

    /** 拿 JWKS 并解析成 RSA 公钥表（带 10 分钟缓存） */
    private Map<String, PublicKey> publicKeys() {
        long now = System.currentTimeMillis();
        if (!keyCache.isEmpty() && now - keyCacheAt < KEY_TTL_MILLIS) {
            return keyCache;
        }
        synchronized (this) {
            if (!keyCache.isEmpty() && System.currentTimeMillis() - keyCacheAt < KEY_TTL_MILLIS) {
                return keyCache;
            }
            try {
                Map<String, Object> jwks = restClient.get()
                        .uri(authority() + "/discovery/v2.0/keys")
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                        });
                Map<String, PublicKey> parsed = new HashMap<>();
                if (jwks != null && jwks.get("keys") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m && "RSA".equals(m.get("kty"))) {
                            String kid = String.valueOf(m.get("kid"));
                            parsed.put(kid, toRsaPublicKey((String) m.get("n"), (String) m.get("e")));
                        }
                    }
                }
                keyCache = parsed;
                keyCacheAt = System.currentTimeMillis();
                log.info("JWKS 刷新完成，公钥数量={}", parsed.size());
                return parsed;
            } catch (Exception e) {
                log.error("获取 JWKS 失败", e);
                throw new BusinessException(ResultCode.SSO_LOGIN_FAILED, "无法获取 Entra ID 签名公钥");
            }
        }
    }

    private PublicKey toRsaPublicKey(String n, String e) throws Exception {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private String readKid(String token) {
        try {
            String[] parts = token.split(".");
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            // 只读取 kid，不做任何信任判断
            int idx = header.indexOf("\"kid\"");
            if (idx < 0) {
                return "";
            }
            int start = header.indexOf('"', idx + 5);
            int end = header.indexOf('"', start + 1);
            return header.substring(start + 1, end);
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- 辅助

    private String authority() {
        return "https://login.microsoftonline.com/" + props.getTenantId();
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(en -> en.getValue().getExpireAt() < now);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256Base64Url(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** mock 模式下的假身份，只在 mockEnabled 时可达 */
    private MsdIdentity mockIdentity(String code) {
        MsdIdentity id = new MsdIdentity();
        id.setIsid("ISID-MOCK-0001");
        id.setDisplayName("Mock Business User");
        id.setEmail("mock.user@msd.example");
        id.setObjectId("mock-oid");
        id.setTenantId("mock-tenant");
        log.warn("MSD SSO mock 模式返回假身份：{}", id.getIsid());
        return id;
    }
}
