# Paperbox Android 项目说明

飞机盒报价工具的 Android 客户端，连接 Node.js 后端 API。

## 技术栈
- Kotlin + Jetpack Compose
- Hilt 依赖注入
- Retrofit + OkHttp 网络请求
- DataStore 本地存储
- Gradle 8.11.1 + AGP 8.7.3

## 项目结构
- `app/src/main/java/com/paperbox/app/` — Kotlin 源码
- `app/src/main/res/` — 资源文件
- `.github/workflows/build-apk.yml` — GitHub Actions 构建配置

## 构建
- 本机无 JDK/Android SDK，构建走 GitHub Actions
- 推送到 `github` 远端触发自动构建
- APK 下载：`https://github.com/ay111128/SuperPower/releases/latest`

## 服务器地址
- API 地址：`https://101.133.169.230`（IP 直连，自签名证书）
- 域名：`https://www.ay111128.com`（TLS 指纹拦截，APP 不能用）
- 后端端口：3101（通过 nginx 代理）

## 开发规则
- 改完代码后不要立马推送git，要得到主人允许才可以推送git
- 提交后推送到 GitHub 触发构建，必须等构建完成
- 不要删除 `gradle/wrapper/` 下的文件
- `isMinifyEnabled = true` 保持开启，否则 APK 膨胀到 13MB

## 给 Claude 的边界规则
- 不要在本地尝试编译（无 JDK/Android SDK）
- 改完代码不要立马推送git
- 提交git必须 `git commit` + `git push github master`
- 推送后必须轮询构建结果，不能推完就不管
- 签名配置在 GitHub Secrets 中，本地不要硬编码
