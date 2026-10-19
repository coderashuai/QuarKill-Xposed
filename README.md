# Quark Kill · QuarKill-Xposed

> 基于 Xposed / LSPosed 的夸克浏览器界面精简模块。通过运行时 Hook 尝试隐藏部分首页入口、快捷按钮和推广元素，并提供实验性的网络请求过滤选项。

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blue)](https://github.com/LSPosed/LSPosed)
[![Build APK](https://github.com/coderashuai/QuarKill-Xposed/actions/workflows/build.yml/badge.svg)](https://github.com/coderashuai/QuarKill-Xposed/actions/workflows/build.yml)

**项目地址：** https://github.com/coderashuai/QuarKill-Xposed

## 项目简介

Quark Kill 旨在让夸克浏览器的界面更简洁。模块以 LSPosed/Xposed 的方式注入目标应用，在运行时调整特定控件的显示状态，**不需要修改或重新打包夸克 APK**。

项目仍属于**实验性适配**。代码针对夸克 `com.quark.browser` 的部分内部类、方法及视图结构编写，主要参考版本为 **10.19.0.1165**；不同版本、设备及界面布局可能表现不同，**不保证所有功能都能生效**。

## 主要功能

设置界面目前提供以下分组和开关，**默认全部关闭**：

| 分组 | 设置项 | 说明 |
| --- | --- | --- |
| 首页精简 | 隐藏搜索框小插件 | 尝试隐藏搜索框加号，并同步控制语音、相机入口 |
| 首页精简 | 隐藏首页 AI 入口 | 尝试隐藏首页部分 AI 控件、悬浮入口 |
| 首页精简 | 禁止左滑进入 AI | 拦截首页特定方向的滑动操作，并同步隐藏模式指示点 |
| 搜索与菜单 | 隐藏小说／书城入口 | 尝试过滤相关工具栏、底部导航和快捷菜单项 |
| 搜索与菜单 | 隐藏首页底栏网盘按钮 | 尝试隐藏底栏网盘或云盘入口 |
| 广告与推广 | 禁止检测和谐网址（手动狗头） | 阻止根据监管部门规定以及投诉举报，此链接违反相关法律法规，禁止访问 |
| 广告与推广 | 实验性网页请求过滤 | 尝试通过 Android `WebViewClient.shouldInterceptRequest` 过滤匹配域名的请求 |
| 界面与维护 | 详细调试日志 | 为部分拦截事件开启附加诊断日志 |

**功能边界：**

- UI 精简依赖夸克内部类名、资源标识、布局及文本；应用更新后可能失效。
- 域名过滤采用**固定规则**，不是完整的广告拦截规则引擎。
- Java DNS Hook 不一定覆盖 Chromium/UC 内核自己的 DNS 解析路径。
- `WebViewClient` 请求拦截不保证覆盖夸克的全部网页请求，更不能保证过滤所有广告。
- 隐藏按钮不代表卸载、停用对应服务，也不保证彻底屏蔽 AI、小说、网盘等后台功能。
- 配置修改后建议**强制停止并重新打开夸克**，确保相关界面重新初始化。

## 使用条件

- Android **8.0（API 26）**及以上（根据项目 `minSdk`）。
- 已安装并正常工作的 **LSPosed / 兼容 Xposed 框架**，设备须具备框架所需环境。
- 目标应用包名为 **`com.quark.browser`** 的夸克浏览器。
- 部分 LSPosed 环境可能需要允许旧版 Xposed 模块入口（项目使用 `assets/xposed_init`）。

> **注意：** Android 系统最低版本与夸克兼容版本是两回事。主要参考的夸克版本为 10.19.0.1165，但实际支持范围尚未经过完整设备矩阵测试。

## 安装与启用

1. 从仓库 [Actions](https://github.com/coderashuai/QuarKill-Xposed/actions/workflows/build.yml) 的成功构建中下载 `QuarkLite-debug-apk` 工件，解压后获取 `app-debug.apk`；也可以自行编译。
2. 安装 APK，打开 **Quark Kill** 设置界面。
3. 在 **LSPosed → 模块** 中启用 Quark Kill；将作用域**仅勾选夸克**（`com.quark.browser`）。
4. 在模块设置页启用需要的功能。
5. 强制停止夸克并重新打开，检查页面效果。

如果模块未生效，先检查 LSPosed 是否已启用模块、作用域是否正确、夸克版本是否与代码适配，并查看框架日志。

## 自愿支持作者

当前设置页包含 **「支持作者 · 自愿打赏」** 入口。

**打赏完全自愿，不影响任何模块功能。**

## 编译 APK

### 使用 GitHub Actions

仓库自带 [`Build APK` 工作流](.github/workflows/build.yml)，在向 `main` / `master` 推送代码时触发，也支持在 Actions 页面手动运行。

工作流使用：

- Ubuntu GitHub 托管 Runner
- JDK 17（Temurin）
- Android SDK Platform 35 / Build Tools 35.0.0
- Gradle 8.10.2
- Android Gradle Plugin 8.6.1

工作流先执行 Java 编译及 Lint，再执行 Debug APK 构建，并上传 APK 工件及可用的 Lint 报告。

> 工作流完成前需要等待 GitHub 分配 Runner。若任务持续处于 `Queued` / `In progress`，先查看 [GitHub Status](https://www.githubstatus.com/)；不能单凭排队状态认定源码编译失败。

### 在电脑上本地编译

准备 **JDK 17、Android SDK 35、Build Tools 35.0.0 和 Gradle 8.10.2**，然后在项目根目录执行：

```bash
gradle :app:compileDebugJavaWithJavac :app:lintDebug --stacktrace
gradle :app:assembleDebug --stacktrace
```

生成路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以使用 Android Studio 打开项目并执行 **Build APK(s)**。本仓库当前构建方式使用系统安装的 `gradle` 命令；如果本地没有 Gradle Wrapper，请先安装适配版本的 Gradle。

**构建说明：** 当前 `release` 配置为 `minifyEnabled false`，未开启 R8 代码混淆。上述命令生成的是用于安装测试的 **Debug APK**，不是面向应用商店的正式签名发行包。

## 项目结构

```text
app/
  build.gradle                          # Android 应用构建参数
  src/main/
    AndroidManifest.xml                 # 模块声明与设置页入口
    assets/xposed_init                  # Xposed Hook 入口
    java/dev/quarklite/
      QuarkLiteHook.java                # 夸克界面与请求 Hook
      LiteConfigProvider.java           # 设置项跨进程读取
      SettingsActivity.java             # 设置界面与支持作者弹窗
.github/workflows/build.yml             # CI 编译与 APK 工件上传
build.gradle                            # Android Gradle Plugin 配置
settings.gradle                         # 模块与依赖仓库配置
```

模块自身包名为 `dev.quarklite`，目标夸克包名为 `com.quark.browser`；请勿混淆。

## 常见问题

**Q：安装后没效果？**  
A：确认 LSPosed 模块开关和目标作用域，强制停止并重启夸克。部分适配依赖具体内部类和视图结构，版本不同可能无效。

**Q：会删除夸克数据或替换原版 APK 吗？**  
A：现有模块以运行时 Hook 为主，不需要重新打包夸克。禁用模块不会主动删除浏览器数据；但任何第三方 Hook 都可能影响目标应用稳定性，建议事先备份重要数据。

**Q：为什么开了广告过滤仍有广告？**  
A：现有实现只针对有限域名及部分 Java DNS/WebView 请求路径，并非全流量拦截器。

**Q：修改设置后为什么没有立即变化？**  
A：部分标志位会被短暂缓存；已经创建的控件也不一定自动恢复或重建。建议强制停止并重启夸克。

**Q：如何彻底恢复原状？**  
A：在模块设置里选择「全部恢复关闭」，然后强制停止并重启夸克；也可以在 LSPosed 中禁用模块，必要时卸载模块 APK。

## 风险与免责声明

本项目是第三方、非官方的实验性 Xposed 模块，与夸克浏览器官方无隶属或合作关系。Hook 可能因应用升级而失效，并可能导致控件显示异常、网页加载问题或应用不稳定。

请自行评估设备环境并承担使用风险；**不保证全部功能、机型或夸克版本兼容**。反馈问题时建议附上 Android 版本、夸克版本、LSPosed 版本、问题复现步骤及相关日志（提交前请移除隐私信息）。

---

## 支持作者 · 自愿打赏

如果 Quark Kill 改善了你的使用体验，欢迎自愿支持项目的后续维护与适配。**打赏完全自愿，不影响任何功能的使用。**

<table>
  <tr>
    <th align="center">微信支付</th>
    <th align="center">支付宝</th>
  </tr>
  <tr>
    <td align="center"><img src="doc/img/wx.jpg" alt="微信支付收款码" width="260" /></td>
    <td align="center"><img src="doc/img/alipay.jpg" alt="支付宝收款码" width="260" /></td>
  </tr>
</table>

> 手机端可以长按图片保存或识别二维码。感谢支持！
