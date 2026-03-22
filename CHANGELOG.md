# 更新日志

本项目的所有重要更改都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.0.7] - 2026-03-22

### UI/UX 全面升级

#### 发现新版本对话框
- 🎨 **Material Design 3 全新设计** - 顶部渐变装饰条、圆形图标背景、胶囊样式标签
- 🎨 **现代化信息展示** - 圆角卡片式版本信息、图标+文字组合更新说明
- 🔧 **优化视觉层次** - 更清晰的信息分组和视觉引导

#### 下载进度对话框重构
- 🎨 **全新现代化UI** - 状态卡片式布局、脉冲动画状态指示器
- ✨ **增强动效设计** - 图标旋转下载动效、状态颜色反馈系统
- ♿ **可访问性优化** - 内容描述、触摸目标优化、高对比度颜色
- 🎨 **信息展示优化** - 速度和存储图标、百分比大字体显示

#### 版本历史页面改进
- 🎨 **渐变工具栏设计** - 更现代的视觉风格
- 🎨 **存储摘要卡片** - 清晰展示下载统计
- 🎨 **圆形选择指示器** - 编辑模式更优雅
- 🎨 **悬浮批量操作栏** - Material Design 3 风格
- 🎨 **空状态优化** - 更友好的引导设计

### 代码质量
- 📝 字符串资源全部提取到 strings.xml
- 📝 新增多个矢量图标资源
- 📝 符合 Kotlin 编码规范

---

## [1.0.6] - 2026-03-22

### Bug修复

- 🐛 **修复下载进度不显示问题** - 将 UpdateManager 改为单例模式，确保所有 Activity 共享同一个下载状态
- 🐛 **修复 StateFlow 状态不同步问题** - 使用单例模式后，下载状态在应用全局保持一致
- 🐛 **修复 Activity 切换后无法获取下载进度问题** - 单例模式确保即使用户离开设置页面再回来，也能正确显示下载进度

### 功能优化

- 🔧 **UpdateManager 单例模式** - 使用双重检查锁定实现线程安全的单例
- 🔧 **统一状态管理** - 所有 Activity 共享同一个 `downloadStatus` 和 `installStatus` StateFlow
- 🔧 **优化资源清理** - 在 `onDestroy` 中正确清理对话框资源

### 技术改进

- 📝 UpdateManager.kt - 改为单例模式，添加 `getInstance()` 方法
- 📝 SettingsActivity.kt - 使用 `UpdateManager.getInstance(this)`
- 📝 VersionHistoryActivity.kt - 使用 `UpdateManager.getInstance(this)`
- 📝 AboutActivity.kt - 使用 `UpdateManager.getInstance(this)`
- 📝 DownloadProgressDialog.kt - 修复视图初始化问题，添加 `pendingVersionName` 机制

---

## [1.0.5] - 2026-03-22

### Bug修复

- 🐛 **修复下载更新跳转问题** - 修复点击"立即下载"后跳转到产品初始页面的问题
- 🐛 **修复 Flow 收集器重复创建问题** - 使用 `repeatOnLifecycle` 确保 Flow 只收集一次
- 🐛 **修复 Activity 重建时状态丢失问题** - 在 `onCreate` 中初始化 Flow 收集，避免重复订阅

### 功能优化

- 🔧 **使用 `repeatOnLifecycle`** - 替代直接 `lifecycleScope.launch`，正确管理 Flow 生命周期
- 🔧 **优化资源清理** - 在 `onDestroy` 中取消协程、关闭对话框，避免内存泄漏
- 🔧 **重构下载流程** - 将 Flow 收集逻辑提取到独立方法，提高代码可维护性

### 技术改进

- 📝 SettingsActivity.kt - 重构 `startDownload()` 方法，使用 `collectDownloadStatus()` 和 `collectInstallStatus()`
- 📝 SettingsActivity.kt - 添加 `downloadJob` 和 `installJob` 管理协程生命周期
- 📝 SettingsActivity.kt - 在 `onDestroy` 中正确清理资源

---

## [1.0.4] - 2026-03-22

### Bug修复

- 🐛 **修复APK安装权限检查流程** - Android 8.0+ 正确请求安装未知来源应用权限
- 🐛 **修复权限授权后无法继续安装** - 使用 ActivityResultLauncher 处理权限授权结果
- 🐛 **修复版本回退功能缺少返回值** - `rollbackToVersion()` 方法添加正确的返回语句

### 功能优化

- 🔧 **新增 Android 11+ 包可见性声明** - 在 AndroidManifest.xml 中添加 `<queries>` 元素
- 🔧 **使用 ActivityResultLauncher** - 替代旧的 `startActivityForResult` 方式处理权限请求
- 🔧 **新增 InstallStatus 状态管理** - 优化安装流程状态跟踪

### 技术改进

- 📝 SettingsActivity.kt - 添加 `installPermissionLauncher` 处理安装权限
- 📝 VersionHistoryActivity.kt - 添加 `installPermissionLauncher` 处理安装权限
- 📝 UpdateManager.kt - 新增 `InstallStatus` 密封类管理安装状态
- 📝 AndroidManifest.xml - 添加 APK 安装相关 Intent 的 queries 声明

### 权限适配说明

| Android 版本 | 权限要求 | 处理方式 |
|-------------|---------|---------|
| 7.0+ | FileProvider | 使用 FileProvider 共享 APK 文件 |
| 8.0+ | REQUEST_INSTALL_PACKAGES | 检查 `canRequestPackageInstalls()` |
| 11+ | 包可见性 | 声明 `<queries>` 元素 |

---

## [1.0.3] - 2026-03-22

### Bug修复

- 🐛 **修复APK无法安装问题** - 添加 `REQUEST_INSTALL_PACKAGES` 权限，支持 Android 8.0+ 应用内安装
- 🐛 **修复FileProvider配置不完整** - 完善 APK 下载目录的 FileProvider 路径配置
- 🐛 **修复安装逻辑问题** - 优化安装流程，添加详细日志和错误处理

### 功能优化

- 🔧 **安装权限检查** - 安装前验证 Intent 是否可解析
- 🔧 **详细日志输出** - 安装过程添加详细日志，便于问题追踪
- 🔧 **错误提示优化** - 安装失败时显示具体错误原因

### UI优化

- 🎨 **重新设计下载进度对话框** - 现代化 Material Design 3 风格
- 🎨 **优化进度显示** - 添加版本信息、下载速度、状态提示
- 🎨 **添加后台下载功能** - 支持后台下载，下载完成后自动安装

---

## [1.0.2] - 2026-03-22

### Bug修复

- 🐛 修复版本号比较逻辑错误，使用语义化版本解析 (`major*10000 + minor*100 + patch`)
- 🐛 修复版本历史删除后UI不更新的问题
- 🐛 修复 GitHub API 速率限制问题，添加 User-Agent 头

### 功能优化

- 🔧 添加网络请求重试机制（最多3次）
- 🔧 添加 GitHub 失败自动切换 Gitee 源功能
- 🔧 连接超时从10秒增加到15秒
- 🔧 添加详细调试日志输出

### 文档更新

- 📝 更新 CONTRIBUTING.md 版本发布规范，添加详细操作清单
- 📝 添加版本发布禁止操作列表

---

## [1.0.1] - 2026-03-22

### Bug修复

- 🐛 修复 `VersionHistoryManager.kt` 中异常处理不规范问题，使用 `Log.e()` 替代 `e.printStackTrace()`
- 🐛 修复 `ImageHostPreferences.kt` 中异常处理不规范问题，使用 `Log.e()` 替代 `e.printStackTrace()`
- 🐛 移除 `DownloadManager.kt` 中冗余的 `@Suppress("UNUSED_VARIABLE")` 注解
- 🐛 移除 `MainActivity.kt` 中冗余的 `@Suppress("DEPRECATION")` 注解

### 代码质量改进

- 🔧 添加 Timber 日志库依赖 (v5.0.1)
- 🔧 添加 EditorConfig 代码风格配置文件
- 🔧 优化异常处理，使用带 TAG 的日志输出

### 技术债务偿还

- 📝 统一日志输出规范
- 📝 建立代码风格统一配置

---

## [1.0.0] - 2026-03-21

### 首次发布

#### 新增功能

**核心功能**
- ✨ Markdown 编辑器核心功能
- ✨ 支持 6 种 Markdown 方言
  - GFM (GitHub Flavored Markdown)
  - CommonMark
  - Original Markdown
  - MultiMarkdown
  - Markdown Extra
  - Pandoc
- ✨ 实时预览模式切换
- ✨ 自动保存功能
- ✨ 字符/行数统计

**图床功能**
- ✨ 集成 6 种图床服务
  - Gitee 图床
  - GitHub 图床
  - 七牛云存储
  - SM.MS 图床
  - Imgur 图床
  - 自定义图床
- ✨ 图片上传历史记录
- ✨ 图床配置管理

**文件操作**
- ✨ 导入 Markdown 文件
- ✨ 导出为 Markdown / HTML / 纯文本
- ✨ 分享功能
- ✨ 复制到剪贴板

**更新系统**
- ✨ 双源更新检查（GitHub/Gitee）
- ✨ 自动选择最优下载源
- ✨ 版本历史记录
- ✨ 下载进度显示
- ✨ 跳过版本功能

**界面设计**
- ✨ Material Design 3 设计语言
- ✨ 深色主题
- ✨ Edge-to-Edge 全面屏适配
- ✨ 流畅的动画效果

**设置功能**
- ✨ Markdown 语法标准选择
- ✨ 下载源设置
- ✨ 关于页面
- ✨ 许可协议展示

#### 技术特性

- 📱 最低支持 Android 7.0 (API 24)
- 📦 安装包体积约 6 MB
- 🔒 本地数据存储，隐私安全
- ⚡ 秒开启动，流畅运行

---

## 版本规划

### [1.1.0] - 计划中

- [ ] 云端同步功能
- [ ] 多文档管理
- [ ] 文件夹组织
- [ ] 搜索功能
- [ ] 导出 PDF

### [1.2.0] - 计划中

- [ ] 自定义主题
- [ ] 快捷键支持
- [ ] 代码高亮增强
- [ ] 表格编辑器

---

## 版本号说明

- **主版本号 (Major)**: 不兼容的 API 修改
- **次版本号 (Minor)**: 向下兼容的功能性新增
- **修订号 (Patch)**: 向下兼容的问题修正

---

[1.0.7]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.7
[1.0.6]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.6
[1.0.5]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.5
[1.0.4]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.4
[1.0.3]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.3
[1.0.2]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.2
[1.0.1]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.1
[1.0.0]: https://github.com/Jay-Victor/Acuspic/releases/tag/v1.0.0
