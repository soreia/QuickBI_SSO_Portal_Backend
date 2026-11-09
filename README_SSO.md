# QuickBI SSO Portal —— MSD Entra ID 单点登录接入说明

## 一、整体链路

```
LoginView.vue
   │ 点击 "Sign in with MSD"
   ▼
GET /api/auth/msd/authorize           ← 后端生成 state / nonce / PKCE
   │ 302 重定向
   ▼
Microsoft Entra ID（MSD IdP）
   │ 用户在 MSD 企业网络内完成认证（按 MSD 策略免 MFA）
   │ 302 带着 code + state 回调
   ▼
前端 /auth/callback（AuthCallbackView.vue）
   │ POST /api/auth/msd/exchange { code, state }
   ▼
后端：code 换 id_token → 验签(JWKS) → 取 ISID → 建档/查用户 → Sa-Token 签发
   │ 返回 { token, userId, username, nickname }
   ▼
前端存 token，跳 /MyReports；后续请求带 Authorization: Bearer <token>
```

要点：认证由 Entra ID 负责，门户不碰密码；签发登录态仍用你现有的 Sa-Token，业务代码不用改。

## 二、改动清单

### 后端 `QuickBI_SSO_Portal_Backend`

| 文件 | 作用 |
| --- | --- |
| `config/MsdSsoProperties.java` | 绑定 `msd.sso.*` 配置 |
| `service/MsdOidcService.java` | 授权 URL 生成、PKCE、code 换 token、JWKS 验签、ISID 提取 |
| `controller/MsdSsoController.java` | `GET /auth/msd/authorize`、`POST /auth/msd/exchange` |
| `entity/dto/MsdExchangeDTO.java` | 前端回传的 code/state |
| `entity/SysUser.java` | 新增 `isid` 字段 |
| `service/SysUserService(+Impl)` | 新增 `loginByIsid()`：按 ISID 查用户，首次自动建档 |
| `common/exception/BusinessException.java` | 新增 `(ResultCode, String)` 构造，便于给出具体原因 |
| `resources/application.yml` | 新增 `msd.sso` 配置块 |
| `sql/init.sql`、`sql/v2_add_isid.sql` | `sys_user` 加 `isid` 列 + 唯一索引 |

### 前端 `QuickBI_SSO_Portal_Frontend`

| 文件 | 作用 |
| --- | --- |
| `api/auth.js` | `startMsdLogin()`、`exchangeMsdCode()`、`logout()` |
| `api/client.js` | 导出 `BASE_URL`、`getToken/setToken`，请求自动带 Bearer |
| `views/AuthCallbackView.vue` | 回调页：拿 code → 换 token → 跳业务页 |
| `views/LoginView.vue` | 登录按钮由 `router.push('/MyReports')` 改为 `startMsdLogin()` |
| `router/index.js` | 新增 `/auth/callback` 路由 |

## 三、需要你提供的四个参数

来自 Azure 门户：**Entra ID → 应用注册 → 你的应用**。

| 配置项 | 从哪拿 |
| --- | --- |
| `msd.sso.tenant-id` | 概览页的「目录(租户) ID」，或 `xxx.onmicrosoft.com` |
| `msd.sso.client-id` | 概览页的「应用程序(客户端) ID」 |
| `msd.sso.client-secret` | 证书和密码 → 新建客户端密码。纯 PKCE 公共客户端可留空 |
| `msd.sso.redirect-uri` | 你自己填，必须与应用注册里「重定向 URI」完全一致 |

建议用环境变量注入，不写死在 yml：

```powershell
$env:MSD_TENANT_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
$env:MSD_CLIENT_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
$env:MSD_CLIENT_SECRET="xxxxx~xxxxx"
$env:MSD_MOCK_ENABLED="false"
```

### 应用注册里还要做三件事

1. **重定向 URI** 填 `http://localhost:5183/auth/callback`（本地），生产换成正式域名。
2. **令牌配置 → 可选声明**：勾选 ISID 对应的 claim（不同租户命名可能是 `isid`、`employeeId`、`extension_xxx`），并把名字填到 `msd.sso.isid-claim`。
3. **API 权限**：至少 `openid`、`profile`、`email`、`offline_access`。

## 四、本地怎么验证

### 1. 先把依赖服务起来

后端启动依赖 MySQL 和 Redis，当前两个都没运行：

```powershell
# MySQL 8.4 已在 D 盘解压安装，确认服务或手动启动 mysqld
# Redis 需要先安装（也可临时把 sa-token 的 redis 集成去掉）
```

建库：

```sql
-- 用 MySQL 客户端执行
source D:\Project\QuickBI\QuickBI_SSO_Portal_Backend\sql\init.sql
```

### 2. 启动后端

```powershell
$mvn = 'C:\Users\suliy\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn.cmd'
& $mvn -B -f 'D:\Project\QuickBI\QuickBI_SSO_Portal_Backend\pom.xml' spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

### 3. 启动前端

```powershell
$node = 'C:\Users\suliy\AppData\Local\Programs\node-v22.20.0-win-x64'
Set-Location 'D:\Project\QuickBI\QuickBI_SSO_Portal_Frontend'
& (Join-Path $node 'npm.cmd') run dev
```

打开 `http://localhost:5183/login`，点「Sign in with MSD」。

### 4. mock 模式（无 Azure 参数时先跑通链路）

`application.yml` 里 `mock-enabled` 默认 `true`：

- `/auth/msd/authorize` 不跳 Entra ID，直接回跳 `http://localhost:5183/auth/callback?code=mock-code&state=xxx`
- `/auth/msd/exchange` 返回固定假身份 `ISID-MOCK-0001`
- 仍会走「查用户 → 建档 → StpUtil.login → 返回 token」的完整逻辑

这样可以在没有真实租户的情况下验证前端跳转、回调页、token 注入、Sa-Token 鉴权是否通。

**接真实租户前必须把 `mock-enabled` 设为 false。**

## 五、几个容易踩的点

1. **redirect-uri 必须逐字符一致**，包括 `http` 与 `https`、端口、结尾有没有 `/`。不一致时报 `AADSTS50011`。
2. **ISID 拿不到**是最常见的对接问题，几乎都是应用注册里没配可选声明，或 claim 名写错。日志里会打印 `id_token 中缺少 xxx 声明`。
3. **state 目前存内存**（`ConcurrentHashMap`），单实例没问题；后端多实例部署时必须换成 Redis，否则用户跳到另一台就 401。
4. **PKCE 与 client_secret 可以并存**：保留 secret 时更严格，去掉也行（公共客户端）。
5. **前端 token 存 localStorage**，XSS 场景有风险。如果要更严格，改成后端 session cookie（HttpOnly）+ SameSite=None，那就需要把 `/api` 与前端同域部署。
6. **MFA 豁免**是 MSD 网络策略给的，不在代码里控制；门户只负责发起 OIDC 请求。

## 六、验证记录

| 项目 | 结果 |
| --- | --- |
| MySQL 8.4.6 | 已启动，127.0.0.1:3306，root/root 可用 |
| 库表 | `quickbi_sso_portal` 已建，`sys_user` 含 `isid` 列 + 唯一索引 |
| Redis | 已安装 5.0.14.1，127.0.0.1:6379，PING 返回 PONG |
| 后端 `mvn compile` | BUILD SUCCESS，27 个源文件 |
| 前端 `npm run lint` | 退出码 0，无告警 |
| 后端启动 | 成功，8081 端口，context path `/api`，耗时 2.95 秒 |
| `GET /api/auth/msd/authorize` | HTTP 302，Location 指向 `http://localhost:5183/auth/callback?code=mock-code&state=...` |
| `POST /api/auth/msd/exchange` | HTTP 200，返回 token，userId=2，username=`ISID-MOCK-0001` |
| 带 Bearer 访问 `/api/auth/userinfo` | HTTP 200，返回该用户完整信息 |
| 自动建档 | `sys_user` 新增一行：id=2，`ISID-MOCK-0001`，昵称 Mock Business User，role=USER |
| 真实 Entra ID 登录 | 未验证，需你提供 tenant-id / client-id / client-secret 并把 `mock-enabled` 置 false |

### 联调中修掉的两个问题

1. **`/auth/msd/**` 被 Sa-Token 拦成 401**：`WebMvcConfig` 白名单原先只有 `/auth/sso/**`，已补 `"/auth/msd/**"`。
2. **Bearer 前缀不识别**：`token-name: Authorization` 时 Sa-Token 默认读原始 token，而前端按 OAuth2 习惯发 `Bearer xxx`。已在 `application.yml` 加 `token-prefix: Bearer`，两边对齐。

### 环境启动方式

`D:\Project\QuickBI\环境启动\` 下有两个脚本，双击即可（当前两个服务已在后台运行）：

- `启动MySQL.bat`
- `启动Redis.bat`

