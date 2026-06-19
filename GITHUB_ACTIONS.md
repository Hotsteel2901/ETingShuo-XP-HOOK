# 使用GitHub Actions自动构建APK

本项目的GitHub Actions工作流会在以下情况自动构建APK：
- 推送到 `main` / `master` / `develop` 分支
- 创建Pull Request
- 手动触发 (在Actions页面点击 "Run workflow")

## 构建流程

### 自动构建
1. 提交代码到GitHub仓库
2. Actions自动触发编译
3. 编译完成后：
   - 未签名APK上传到Artifacts (保留30天)
   - 已签名APK上传到Artifacts (保留30天)
   - 如果是main/master分支，自动创建Release

### 手动触发
1. 进入仓库的 **Actions** 标签
2. 选择 **Build Xposed Module APK** 工作流
3. 点击 **Run workflow**
4. 选择分支 → 点击运行

## 下载APK

### 方式1: 从Artifacts下载
1. 进入Actions → 选择成功的工作流运行
2. 滚动到底部的 **Artifacts** 部分
3. 点击下载:
   - `ETingshuoVipHook-signed` — 已签名可直接安装的APK
   - `ETingshuoVipHook-unsigned` — 未签名的APK

### 方式2: 从Releases下载 (仅main分支)
1. 进入仓库的 **Releases** 标签
2. 选择最新版本
3. 下载 `ETingshuoVipHook-final.apk`

## 工作流详解

`.github/workflows/build-apk.yml` 包含以下步骤:

| 步骤 | 说明 |
|------|------|
| Checkout | 拉取代码 |
| JDK 11 | 设置Java 11环境 |
| Android SDK | 安装Android SDK 34、build-tools、platform-tools |
| Xposed API | 下载XposedBridgeApi-54.jar到app/libs |
| 编译 | `./gradlew assembleRelease` |
| 签名 | 使用debug key签名APK |
| 对齐 | zipalign优化APK |
| 上传 | 上传到GitHub Artifacts |
| Release | (main分支)自动创建Release |

## 本地编译

如果不想使用GitHub Actions，可以本地编译：

```bash
# 1. 安装JDK 11
# 2. 安装Android SDK 34
# 3. 下载Xposed API
mkdir -p app/libs
wget https://api.xposed.info/download/XposedBridgeApi-54.jar -O app/libs/XposedBridgeApi-54.jar

# 4. 编译
./gradlew assembleRelease

# 5. 签名 (可选)
keytool -genkey -keystore release.jks -alias hook
apksigner sign --ks release.jks --out ETingshuoVipHook.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## 环境变量

工作流使用以下环境变量，无需配置：

| 变量 | 值 | 说明 |
|------|-----|------|
| `GITHUB_TOKEN` | 自动提供 | 用于创建Release |

## 注意事项

1. **Xposed API jar**: 工作流会自动从 `https://api.xposed.info/download/XposedBridgeApi-54.jar` 下载
2. **签名**: 使用Android debug签名，可用于测试和开发
3. **Release**: 仅在推送到main/master分支时自动创建
4. **Artifacts**: 保留30天，超期自动删除

## 故障排查

### 编译失败
- 检查Java版本是否为11
- 检查build.gradle配置是否正确

### 签名失败
- 检查keytool命令是否正确
- 确保build-tools已安装

### APK无法安装
- 检查签名是否有效
- 检查minSdk版本是否满足设备要求 (当前为26)