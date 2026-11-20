# PZip

基于官方 **7-Zip** 的扁平化现代压缩软件。技术栈：**Tauri 2 + Rust + Web UI**，仅 Windows。

界面参考 Bandizip 的扁平化风格，功能尽量对齐 7-Zip：压缩 / 解压 / 测试 / 列表、加密、分卷、SFX 自解压、哈希、基准测试、右键菜单集成，中英双语。

## 功能特性

- 🗜️ **全格式兼容**：底层捆绑官方 7-Zip（`7z.exe` + `7z.dll`），支持 7z / ZIP / TAR / WIM / XZ / GZIP / BZIP2 的压缩，以及 RAR / ISO / CAB 等约 50 种格式的解压
- 🔐 **AES-256 加密**（7z / ZIP），支持加密文件名
- 📦 **分卷压缩**、**SFX 自解压 exe**
- # **校验和**：CRC32 / CRC64 / SHA1 / SHA256 / SHA512 / SHA3 …
- ⚡ **基准测试**（内置 7-Zip benchmark）
- 🖱 **右键菜单集成**（"添加到压缩包" / "解压到当前文件夹" / "解压到文件夹"）
- 🌐 **中英双语**、扁平化现代 UI、拖拽、实时进度

## 目录结构

```
pzip/
├── src/                    # 前端（Vite + 原生 JS）
│   ├── index.html
│   ├── main.js
│   └── styles.css
├── src-tauri/
│   ├── src/                # Rust 后端
│   │   ├── main.rs
│   │   ├── lib.rs          # 命令注册
│   │   ├── commands.rs     # Tauri 命令
│   │   ├── sevenzip.rs     # 7-Zip 封装（进度/列表/哈希解析）
│   │   └── shell.rs        # 右键集成（HKCU 注册表）
│   ├── binaries/           # 捆绑的 7-Zip 二进制（7z.exe/7z.dll/7z.sfx/7zCon.sfx + 许可证）
│   ├── icons/              # 应用图标
│   └── tauri.conf.json
├── scripts/                # 下载 7-Zip 二进制、生成图标
└── .github/workflows/      # GitHub Actions 自动打包
```

## 用 GitHub Actions 打包（推荐，无需本地工具链）

本仓库已配置好 CI，推送代码或手动触发即可在 Windows runner 上自动编译打包：

1. 进入仓库 **Actions** 标签页 → 选择 **Build PZip** 工作流 → **Run workflow**
2. 等待构建完成，在运行详情页底部的 **Artifacts** 下载：
   - `pzip-windows-nsis`：NSIS 安装程序（.exe）
   - `pzip-windows-msi`：MSI 安装程序

## 本地构建（需要 Windows + 完整工具链）

前置依赖：

- [Rust](https://rustup.rs/)（stable，MSVC 工具链）
- [Node.js](https://nodejs.org/) ≥ 18
- [Visual Studio Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/)（含 "使用 C++ 的桌面开发"）
- Windows 10/11（自带 WebView2）

```bash
npm install
npm run tauri build
```

产物在 `src-tauri/target/release/bundle/` 下。

开发模式：`npm run tauri dev`

## 7-Zip 集成说明（许可证）

本软件捆绑了未修改的官方 7-Zip 26.06 二进制（`7z.exe`、`7z.dll`、`7z.sfx`、`7zCon.sfx`）。

- 7-Zip 版权归 **Igor Pavlov** 所有，采用 **GNU LGPL** 许可证分发
- 源代码：<https://www.7-zip.org/>
- 完整许可证见 `src-tauri/binaries/7z-LICENSE.txt`
- 二进制可通过 `node scripts/download-7zip.mjs` 重新下载

## License

本项目代码采用 MIT 许可证；所捆绑的 7-Zip 组件遵循其各自的 LGPL 许可证。
