# 贡献指南

感谢你考虑为 Acuspic 做出贡献！

---

## 目录

- [行为准则](#行为准则)
- [如何贡献](#如何贡献)
- [开发环境搭建](#开发环境搭建)
- [代码规范](#代码规范)
- [提交规范](#提交规范)
- [Pull Request 流程](#pull-request-流程)
- [版本发布规范](#版本发布规范)
- [问题反馈](#问题反馈)

---

## 行为准则

本项目采用贡献者公约作为行为准则。参与本项目即表示你同意遵守其条款。请阅读 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) 了解详情。

---

## 如何贡献

### 报告 Bug

如果你发现了 Bug，请通过 [GitHub Issues](https://github.com/Jay-Victor/Acuspic/issues) 提交报告。

**Bug 报告应包含：**

1. **标题** - 简洁描述问题
2. **环境信息**：
   - Android 版本
   - 设备型号
   - Acuspic 版本
3. **重现步骤** - 详细描述如何重现问题
4. **预期行为** - 你期望发生什么
5. **实际行为** - 实际发生了什么
6. **截图** - 如果适用，添加截图说明问题
7. **日志** - 如果有错误日志，请附上

### 建议新功能

欢迎提出新功能建议！请在 Issue 中详细描述：

1. 功能描述
2. 使用场景
3. 预期效果

### 提交代码

1. Fork 本仓库
2. 创建功能分支
3. 编写代码
4. 提交 Pull Request

---

## 开发环境搭建

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+ |
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| Gradle | 8.4 |
| Kotlin | 1.9.22 |

### 克隆项目

```bash
git clone https://github.com/Jay-Victor/Acuspic.git
cd Acuspic
```

### 导入项目

1. 打开 Android Studio
2. 选择 "Open an Existing Project"
3. 选择项目目录
4. 等待 Gradle 同步完成

### 构建项目

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

### 项目结构

```
Acuspic/
├── android/                # Android 项目
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/       # Kotlin 源码
│   │   │   └── res/        # 资源文件
│   │   └── build.gradle.kts
│   └── gradle/
├── ic_launcher/            # 旧版图标
├── new_ic_launcher/        # 新版图标
├── 产品截图/                # 应用截图
└── 个人收款码/              # 打赏二维码
```

---

## 代码规范

### Kotlin 代码风格

- 遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 4 个空格缩进
- 类和函数使用 PascalCase 命名
- 变量和参数使用 camelCase 命名
- 常量使用 UPPER_SNAKE_CASE 命名

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Activity | *Activity | `MainActivity` |
| Fragment | *Fragment | `SettingsFragment` |
| Adapter | *Adapter | `HistoryAdapter` |
| ViewModel | *ViewModel | `MainViewModel` |
| 布局文件 | activity_*, fragment_* | `activity_main.xml` |
| 资源 ID | snake_case | `@+id/edit_text` |

### 注释规范

```kotlin
/**
 * 类/函数说明
 * 
 * @param paramName 参数说明
 * @return 返回值说明
 */
fun functionName(paramName: Type): ReturnType {
    // 实现
}
```

### 资源文件规范

- 字符串资源放在 `values/strings.xml`
- 颜色资源放在 `values/colors.xml`
- 布局文件使用 ConstraintLayout 或 LinearLayout
- 图片资源使用矢量图（Vector Drawable）

---

## 提交规范

### Commit Message 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| Type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 重构（不是新功能也不是修复） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建过程或辅助工具变动 |

### 示例

```
feat(editor): 添加表格插入功能

- 支持通过工具栏插入表格
- 自动生成表格模板
- 支持 GFM 表格语法

Closes #123
```

---

## Pull Request 流程

### 1. Fork 并克隆

```bash
git clone https://github.com/YOUR_USERNAME/Acuspic.git
cd Acuspic
git remote add upstream https://github.com/Jay-Victor/Acuspic.git
```

### 2. 创建分支

```bash
git checkout -b feature/your-feature-name
```

分支命名规范：
- `feat/` - 新功能
- `fix/` - Bug 修复
- `docs/` - 文档更新
- `refactor/` - 重构

### 3. 编写代码

- 遵循代码规范
- 添加必要的注释
- 编写单元测试（如适用）

### 4. 提交更改

```bash
git add .
git commit -m "feat: your feature description"
```

### 5. 推送分支

```bash
git push origin feature/your-feature-name
```

### 6. 创建 Pull Request

1. 访问你 Fork 的仓库页面
2. 点击 "New Pull Request"
3. 填写 PR 描述：
   - 更改内容说明
   - 关联的 Issue
   - 测试结果

### PR 审核标准

- ✅ 代码符合规范
- ✅ 功能正常工作
- ✅ 无明显 Bug
- ✅ 有适当的测试
- ✅ 文档已更新

---

## 问题反馈

### 联系方式

| 渠道 | 信息 |
|------|------|
| GitHub Issues | https://github.com/Jay-Victor/Acuspic/issues |
| 邮箱 | 18261738221@163.com |
| QQ | 1061037299 |
| QQ群 | 1091235240 |

### 反馈模板

**Bug 反馈：**

```
**问题描述**
[简洁描述问题]

**环境信息**
- Android 版本：
- 设备型号：
- Acuspic 版本：

**重现步骤**
1. 
2. 
3. 

**预期行为**
[描述预期发生什么]

**实际行为**
[描述实际发生了什么]

**截图**
[如有，请附上截图]

**日志**
```
[如有错误日志，请粘贴]
```
```

**功能建议：**

```
**功能描述**
[描述你想要的功能]

**使用场景**
[描述什么情况下需要这个功能]

**预期效果**
[描述功能应该如何工作]
```

---

## 版本发布规范

### 发布流程

1. **更新版本号**
   - 在 `android/app/build.gradle.kts` 中更新 `versionCode` 和 `versionName`
   - 更新 `CHANGELOG.md` 添加版本更新日志

2. **构建 Release APK**
   ```bash
   ./gradlew assembleRelease
   ```

3. **组织发布文件**
   
   构建完成后，在 `android/app/build/outputs/apk/release/` 目录下创建版本文件夹：
   
   ```
   release/
   └── v{版本号}/           # 例如: v1.0.0, v1.1.0
       ├── Acuspic-v{版本号}.Apk    # APK 安装包
       ├── Acuspic-v{版本号}.md     # 版本说明文档
       └── output-metadata.json    # 构建元数据
   ```

4. **版本说明文档模板**
   
   每个版本文件夹中应包含 `Acuspic-v{版本号}.md` 文件，内容包括：
   - 版本简介
   - 功能特性
   - 更新内容
   - 下载安装说明
   - 相关链接

### 版本文件夹命名规范

| 版本类型 | 命名格式 | 示例 |
|----------|----------|------|
| 正式版本 | `v{major}.{minor}.{patch}` | `v1.0.0`, `v1.1.0` |
| 测试版本 | `v{major}.{minor}.{patch}-beta.{n}` | `v1.1.0-beta.1` |
| 候选版本 | `v{major}.{minor}.{patch}-rc.{n}` | `v1.1.0-rc.1` |

### ⚠️ 重要注意事项

1. **保留历史版本**
   - 旧版本文件夹永久保留，**禁止删除或修改**
   - 每个版本都是独立的，互不影响

2. **增量创建原则**
   - 发布新版本时，只创建新版本文件夹
   - **不要移动或覆盖**旧版本文件

3. **版本完整性**
   - 每个版本文件夹必须包含完整的三个文件：
     - `Acuspic-v{版本号}.Apk` - 安装包
     - `Acuspic-v{版本号}.md` - 说明文档
     - `output-metadata.json` - 构建元数据

4. **操作前检查**
   - 创建新版本前，先确认 `release/` 目录结构
   - 确保不会影响已存在的版本文件夹

### 示例目录结构

```
android/app/build/outputs/apk/release/
├── v1.0.0/
│   ├── Acuspic-v1.0.0.Apk
│   ├── Acuspic-v1.0.0.md
│   └── output-metadata.json
├── v1.1.0/
│   ├── Acuspic-v1.1.0.Apk
│   ├── Acuspic-v1.1.0.md
│   └── output-metadata.json
└── v1.2.0-beta.1/
    ├── Acuspic-v1.2.0-beta.1.Apk
    ├── Acuspic-v1.2.0-beta.1.md
    └── output-metadata.json
```

---

## 许可证

通过向本项目贡献代码，你同意你的代码将按照本项目的许可证进行授权。

详见 [LICENSE](LICENSE) 文件。

---

再次感谢你的贡献！❤️
