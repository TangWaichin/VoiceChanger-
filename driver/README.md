# driver — 可选第三方虚拟声卡组件

本目录**不包含**未完成的自研虚拟声卡驱动（`driver/sysvad`）及其安装/验证脚本。
自研驱动工程已从 GitHub 源码包中剔除。

目录内容：

```text
driver/
├── vbcable/                  # VB-CABLE 原始安装包（未修改）
└── install/
    ├── install-vbcable.ps1   # 静默安装 VB-CABLE
    └── uninstall-vbcable.ps1 # 静默卸载 VB-CABLE
```

## VB-CABLE

`vbcable/` 来自 VB-Audio 官网的 `VBCABLE_Driver_Pack46` 原包，版权归 VB-Audio 所有。
请勿修改包内文件；其许可与捐赠说明见 `vbcable/readme.txt` 和 https://vb-cable.com。
本目录中的 `install/` 脚本属于本项目，适用仓库根目录的 MIT License；`vbcable/` 不适用 MIT License。

安装/卸载脚本需要管理员权限，内部使用官方安装器的静默参数：

```powershell
# 安装（PowerShell 管理员）
powershell -ExecutionPolicy Bypass -File .\install\install-vbcable.ps1 -PackageDir .\vbcable

# 卸载（PowerShell 管理员）
powershell -ExecutionPolicy Bypass -File .\install\uninstall-vbcable.ps1
```

安装后系统会出现 `CABLE Input` / `CABLE Output` 端点；卸载后建议重启 Windows 完成清理。

## 源码运行时

从源码运行 JAR 时，应用目录是 `build/`。若要在“设置 → 虚拟声卡”中测试 VB-CABLE 一键安装，
可将本目录复制到 `build/driver/`，或直接按 VB-CABLE 官方说明手动安装。

自研虚拟声卡按钮在源码包中不可用，属于预期行为。
