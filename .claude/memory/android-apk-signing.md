---
name: android-apk
description: Paperbox Android 项目 GitHub Actions 构建 APK 的签名踩坑记录
metadata:
  node_type: memory
  type: project
  originSessionId: dc869cf3-3f46-4034-92b2-2e78f2147aaa
  modified: 2026-09-12T01:27:29.437Z
---

## 项目路径

- Android 项目：`/home/ubuntu/paperbox-Android/`
- GitHub 仓库：`https://github.com/ay111128/SuperPower.git`
- 工作流：`.github/workflows/build-apk.yml`

## Keystore

- 文件：`app/release-key.jks`（实际是 PKCS12 格式，不是 JKS）
- 别名：`key0`（Android Studio 默认）
- 密码：`ay111128`
- 原始文件：`/home/ubuntu/aliyun_sshkey/apk-keys`

## 踩过的坑（按时间顺序）

### 1. 未签名 APK → "Package info is null"

**症状**：小米手机安装 APK 显示"解析软件包时出现问题"

**根因**：Gradle 没有成功签名，输出 `app-release-unsigned.apk`

### 2. `file()` 路径解析陷阱

**根因**：`android {}` 块内的 `file()` 是相对于 `app/` 模块目录解析的。如果环境变量是 `app/release-key.jks`，`file()` 会解析为 `app/app/release-key.jks`（路径重复）。

**修复**：用 `substringAfterLast("/")` 取文件名：
```kotlin
storeFile = file(System.getenv("SIGNING_STORE_FILE")?.substringAfterLast("/") ?: "release-key.jks")
```

### 3. `if (file(ksFile).exists())` 导致签名被跳过

**根因**：用 `if` 判断 keystore 是否存在，但条件不满足时 `signingConfig` 为 null，Gradle 不会签名。

**修复**：移除 `if` 判断，直接赋值。如果 keystore 不存在构建会失败（更安全）。

### 4. Keystore 别名

- 尝试过 `release`（错）、`Key 0`（错）、`mykey`（错）
- 正确答案是 `key0`（Android Studio 默认 PKCS12 别名）
- 用 `keytool -list -keystore xxx.jks -storepass xxx` 可以列出所有别名

### 5. APK 体积：13MB → 2MB 的实现方式

**原因**：`isMinifyEnabled = false` 时不裁剪依赖库，所有 Compose/Material3/Retrofit/OkHttp 等全部打入 APK，膨胀到 13MB

**实现 2MB 的关键配置**（`app/build.gradle.kts`）：
```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true      // R8 代码裁剪（关键！）
        isShrinkResources = true    // 资源裁剪
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

**原理**：
- R8（ProGuard 替代品）会分析代码依赖树，删除未使用的类、方法、字段
- `isShrinkResources` 删除未引用的资源文件（图片、布局等）
- Compose 的 inline 函数在编译期展开，R8 能有效裁剪
- 结果：从 13MB 压缩到 ~2MB（含签名）

**注意**：如果禁用 minify（`isMinifyEnabled = false`），APK 会立即膨胀回 13MB+

### 6. jarsigner vs apksigner

- `jarsigner`：只创建 v1 签名（JAR 签名），日志显示 `jar is unsigned` 但实际可能有 v2/v3
- `apksigner`：创建 v1/v2/v3 签名，Android 7+ 需要 v2+
- Gradle 的 `signingConfig` 内部用 apksigner，会自动创建 v2/v3

## 正确的签名配置（最终版）

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_STORE_FILE")?.substringAfterLast("/") ?: "release-key.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(...)
        }
    }
}
```

## GitHub Actions workflow 环境变量

```yaml
env:
  SIGNING_STORE_FILE: app/release-key.jks
  SIGNING_STORE_PASSWORD: ay111128
  SIGNING_KEY_ALIAS: key0
  SIGNING_KEY_PASSWORD: ay111128
```

## 验证方法

```bash
# 查看 keystore 别名
keytool -list -keystore release-key.jks -storepass ay111128

# 验证 APK 签名（v1）
jarsigner -verify -verbose -certs app.apk

# 验证 APK 签名（v2/v3）
apksigner verify --print-certs app.apk
```

## APK 大小参考

- minify 开启 + 签名：~2MB
- minify 关闭：~13MB

---

## Gradle Wrapper 踩坑（2026-09-11 新增）

### 7. gradlew 脚本损坏 — `Error: Could not find or load main class "-Xmx64m"`

**症状**：`./gradlew assembleRelease` 报 `ClassNotFoundException: "-Xmx64m"`

**根因**：`gradlew` 脚本有两个严重 bug：
1. `CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar`（第47行）在 `APP_HOME`（第65行）**之前**设置 → `APP_HOME` 为空，`CLASSPATH` 变成 `/gradle/wrapper/gradle-wrapper.jar`
2. `DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'` 单引号包裹导致内层双引号成为字面量 → bash word-split 后 Java 收到 `"-Xmx64m"`（带引号）被当作类名

**修复**：从 Gradle 官方仓库下载标准 gradlew 脚本（`raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew`）

### 8. gradle-wrapper.jar 被误删

**症状**：`ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`

**根因**：之前提交 `6c91333` 删除了 `gradle-wrapper.jar`，且 `gradle-wrapper.properties` 从未提交

**修复**：
- 恢复 jar：`git show eeaa718:gradle/wrapper/gradle-wrapper.jar > gradle/wrapper/gradle-wrapper.jar`
- 创建 properties：指定 `distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip`

### 9. gradle/actions/setup-gradle wrapper 校验失败

**症状**：`Error: At least one Gradle Wrapper Jar failed validation!`

**根因**：`gradle/actions/setup-gradle@v4` 默认校验 wrapper jar 的 SHA-256 是否匹配已知版本，恢复的 jar checksum 不在白名单中

**修复**：
- workflow 中加 `validate-wrappers: false`（`with` 块内）
- `GRADLE_WRAPPER_DISABLE_VALIDATION: true`（env 块内，作为兜底）

### 10. 最终方案：跳过 wrapper 直接用 gradle

**发现**：`setup-gradle` action 设置 `gradle-version: '8.11.1'` 后，系统 PATH 中有 `gradle` 命令可直接用，完全绕开 wrapper jar 问题

**workflow 最终写法**：
```yaml
- name: Build Release APK
  run: gradle assembleRelease --no-daemon --build-cache
```
而非 `./gradlew assembleRelease`。

### 重要规则

- **不要删除 `gradle/wrapper/` 下的文件**，它们是构建必需的
- **修改 gradlew 前先备份**，标准脚本的 APP_HOME/CLASSPATH 顺序和引号处理是经过验证的
- **如果 wrapper 出问题**，优先在 workflow 中改用 `gradle` 命令 + `gradle-version` 参数，而非修 wrapper
