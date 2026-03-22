# Acuspic v1.0.4

## 更新说明

### 新增功能
- Android 11+ 包可见性声明，确保APK安装Intent查询正常工作

### 问题修复
- 修复APK安装权限检查流程，Android 8.0+ 正确请求安装未知来源应用权限
- 修复权限授权后无法继续安装的问题
- 修复版本回退功能缺少返回值的问题

### 优化改进
- 使用 ActivityResultLauncher 替代旧的权限请求方式
- 新增 InstallStatus 状态管理，优化安装流程状态跟踪
- 优化下载完成后的安装提示交互
- 权限授权返回后自动继续安装流程

## 技术细节

### 权限适配
| Android 版本 | 权限要求 | 处理方式 |
|-------------|---------|---------|
| 7.0+ | FileProvider | 使用 FileProvider 共享 APK 文件 |
| 8.0+ | REQUEST_INSTALL_PACKAGES | 检查 canRequestPackageInstalls() |
| 11+ | 包可见性 | 声明 queries 元素 |

### 文件变更
- `AndroidManifest.xml` - 添加 queries 声明
- `UpdateManager.kt` - 新增 InstallStatus 状态管理
- `SettingsActivity.kt` - 使用 ActivityResultLauncher 处理权限
- `VersionHistoryActivity.kt` - 使用 ActivityResultLauncher 处理权限

## 版本信息
- 版本号: 1.0.4
- 版本代码: 5
- 发布日期: 2026-03-22
- 最低SDK: 24 (Android 7.0)
- 目标SDK: 36 (Android 16)
