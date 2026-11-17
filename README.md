# VoiceChanger 1.0.0.0

Windows 实时麦克风变声软件，Java Swing 界面 + C++ WASAPI 原生音频引擎。
本仓库是从开发工作区整理出的**可公开源代码包**，用于在 GitHub 上发布和协作。

## 功能概览

- 12 个内置调音模块：噪声门、变调、共振峰、机器人、失真、均衡、压缩、合唱、颤音、回声、混响、输出。
- WASAPI 共享模式原生采集/渲染，失败时自动回退 Java Sound。
- 48 kHz / 单声道 / 32bit 浮点内部处理，对外输出 16bit PCM。
- 中英文界面切换。
- 本地自定义调音接口：127.0.0.1:47801 / 47802，48kHz / 单声道 / PCM16LE，每块 480 帧。
- 可选耳返监听、调音编码导入导出、本地存档。

## 仓库不包含的内容

为避免把未完成组件和二进制产物发布到 GitHub，本包已剔除：

- `driver/sysvad/`：未完成的自研虚拟声卡驱动工程及驱动包。
- `install-driver.ps1`、`uninstall-driver.ps1`、`verify-driver.ps1`、`LoopbackTest.cs`、`devcon.exe`：自研驱动的安装/卸载/验证工具。
- `build/installer/`、`dist/`：exe/MSI 安装包和便携运行时。
- `tools/build-installer.ps1`：jpackage + WiX 安装包打包方式。
- `resources/app.ico`、代码签名公钥等安装包专用资源。
- `DemoWeb/`、内部 `AGENTS.md`、版本备份目录与构建日志。

因此，源码运行时“设置 → 虚拟声卡”中针对自研驱动的按钮会提示“未找到虚拟声卡驱动包”，这是预期行为，不影响其他功能。

## 目录结构

```text
VoiceChanger1.0.0.0Github/
├── build.ps1                  # Java 编译、资源复制、JAR 与测试工具构建
├── 启动变声器.bat              # 构建并启动 JAR
├── src/main/java/             # Java 源码
├── src/main/resources/        # 窗口/任务栏 PNG 图标
├── native/
│   ├── build.ps1              # MinGW-w64 构建 VoiceChangerNative.dll
│   └── src/                   # C++ WASAPI / JNI 源码
├── tools/                     # 自测、原生测试、UI 截图、设备探测工具
├── examples/passthrough.py    # 自定义调音接口原样转发示例
└── driver/                    # 可选的第三方 VB-CABLE 组件（见其 README）
```

## 环境要求

- Windows 10 / 11 x64
- JDK 21 或更高版本（开发时使用 JDK 27），并设置 `JAVA_HOME`
- 可选：MinGW-w64 g++（构建原生 WASAPI DLL）
- 可选：VB-CABLE / JinZhou Virtual Device 等虚拟声卡（用于会议、直播软件读取变声结果）

## 构建

在仓库根目录打开 PowerShell：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk"
.\build.ps1
```

产物：

```text
build/VoiceChanger.jar
build/test-classes/          # 测试工具
```

原生音频引擎为可选项；不构建时自动使用 Java Sound 后端。

```powershell
$env:MINGW_HOME = "C:\path\to\mingw64"
.\native\build.ps1
```

产物：

```text
build/native/VoiceChangerNative.dll
```

## 运行

```text
启动变声器.bat
```

或手动：

```powershell
javaw -jar build\VoiceChanger.jar
```

## 虚拟声卡说明

本仓库不包含自研虚拟声卡驱动。推荐自行安装第三方虚拟声卡，例如：

- VB-CABLE：https://vb-cable.com
- JinZhou Virtual Device

安装后，在软件“设置”中：

1. 音频信号源选择真实麦克风。
2. 变声输出选择虚拟声卡的播放端（如 `CABLE Input`）。
3. 在会议/直播软件中选择同一虚拟声卡的录音端（如 `CABLE Output`）。

`driver/vbcable/` 保留了可选的 VB-CABLE 原始安装包与安装/卸载脚本，具体见 `driver/README.md`。
源码运行时应用目录是 `build/`；如需测试应用内一键安装，可将 `driver/` 复制到 `build/driver/`。

## 测试工具

`tools/` 下提供：

- `SelfTest.java`：DSP、模块注册与基础音频格式自测。
- `I18nTest.java`：中英文切换与存档语言恢复。
- `IconTest.java`：窗口图标尺寸加载。
- `NativeAudioTest.java`、`EngineNativeTest.java`：原生采集/引擎测试。
- `ProbeDevices.java`：设备枚举。
- `UiShot.java`：界面截图。

构建后运行示例：

```powershell
java -cp "build\classes;build\test-classes" SelfTest
java -cp "build\classes;build\test-classes" I18nTest
```

## 开发说明

- Java 源码使用 UTF-8。
- `build.ps1` 在 Windows PowerShell 5 下运行；新增脚本注释建议使用 ASCII，避免编码导致解析问题。
- 日志与存档默认位于用户数据目录；升级或迁移前请自行备份。
- 修改 `App.VERSION` 时同步更新 README 与发布标签，但不要修改 `PresetStore.VERSION`（它是存档格式版本）。

## 许可证

本项目源代码采用 **MIT License**，完整文本见 [LICENSE](LICENSE)。
Copyright (c) 2026 TangWaaichin。

`driver/vbcable/` 为 VB-Audio 的第三方组件，**不适用** MIT License，版权归 VB-Audio 所有，
遵循其自带 `readme.txt` 与官网许可；本仓库未修改该目录内容。VB-CABLE 为 donationware，
建议保留其署名与捐赠入口。

## 版本

- 应用版本：1.0.0.0
- 存档格式版本：1
