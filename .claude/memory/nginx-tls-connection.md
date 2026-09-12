---
name: nginx-tls-connection
description: Nginx 路由配置、TLS 指纹拦截、IP 直连自签名证书等网络问题排查经验
metadata: 
  node_type: memory
  type: project
  modified: 2026-09-12T02:03:39.968Z
  originSessionId: dc869cf3-3f46-4034-92b2-2e78f2147aaa
---

## Nginx 路由配置

### 新增 API 端点必须添加 nginx location 块

**教训**：后端新增 `/auth/` 端点后，Android APP 登录报 "connection reset"。原因是 nginx 没有 `/auth/` location 块，请求被转发到 Next.js (端口 3002) 而非后端 (端口 3101)。

**修复**：在 nginx HTTPS server 块中添加：
```nginx
location /auth/ {
  proxy_pass http://127.0.0.1:3101;
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_buffering off;
  proxy_read_timeout 600s;
  proxy_send_timeout 600s;
}
```

**规则**：后端每新增一个 API 路由前缀，都要在 nginx 中添加对应的 location 块。

### Nginx server_name 匹配优先级

**问题**：IP 直连 `https://101.133.169.230` 返回了域名 `www.ay111128.com` 的证书。

**原因**：多个 server 块监听 443 端口，nginx 按 server_name 匹配，IP 连接匹配到了第一个 server 块（域名块）。

**修复**：IP server 块添加 `default_server`：
```nginx
listen 443 ssl http2 default_server;
listen [::]:443 ssl http2 default_server;
```
同时移除其他 server 块的 `default_server`，避免冲突。

---

## TLS 指纹拦截 / Connection Reset

### 问题现象

Android APP (OkHttp) 连接 `https://www.ay111128.com` 报 "connection reset"，但手机浏览器可以正常访问。

### 根因

阿里云安全策略基于 TLS 指纹（JA3/JA4）拦截特定客户端。OkHttp 的 TLS 指纹与浏览器不同，被识别为可疑连接并重置。

### 解决方案：IP 直连 + 自签名证书

1. **生成自签名证书**（在服务器上）：
```bash
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout /etc/nginx/ip-key.pem \
  -out /etc/nginx/ip-cert.pem \
  -subj "/CN=101.133.169.230" \
  -addext "subjectAltName=IP:101.133.169.230"
```

2. **Nginx 添加 IP server 块**（使用自签名证书）

3. **Android APP 配置**：
   - `build.gradle.kts`: `API_BASE_URL` 改为 `"https://101.133.169.230"`
   - `network_security_config.xml`: 信任自签名证书
   - 将证书放入 `res/raw/ip_cert.pem`

### 网络安全配置模板

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">101.133.169.230</domain>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/ip_cert" />
        </trust-anchors>
    </domain-config>
</network-security-config>
```

---

## GitHub Actions Release 说明自定义

### 用 commit message 替代固定文案

```yaml
- name: Generate release body
  run: |
    BUILD_TIME=$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M')
    SHORT_SHA=$(echo "${{ github.sha }}" | cut -c1-7)
    COMMIT_MSG=$(echo "${{ github.event.head_commit.message }}" | head -1)
    {
      echo "**${BUILD_TIME}**（${SHORT_SHA}）"
      echo ""
      echo "${COMMIT_MSG}"
    } > release_body.md
```

然后用 `body_path: release_body.md` 替代 `generate_release_notes: true`。

---

## 记住密码功能实现

使用 DataStore 存储用户名和密码：
- `PrefsKeys.SAVED_USERNAME` / `SAVED_PASSWORD` / `REMEMBER_PASSWORD`
- 登录成功后根据复选框状态保存或清除
- LaunchedEffect 中加载已保存的凭据
