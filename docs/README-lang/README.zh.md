<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-0048FF?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">桌面版</a>] · iOS 开发中</b><br>
<b>非官方 SoundCloud Android 客户端</b><br>
无广告 · 无验证码 · 无审查 · 在俄罗斯可用
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-Android?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-Android/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-Android/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
</a>

<a href="../../LICENSE">
<img src="https://img.shields.io/badge/License-MIT-0048FF?style=for-the-badge" alt="License"/>
</a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/下载_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

## 这是什么？

**SoundCloud Android** 是一款原生的手机 SoundCloud 客户端，与 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 同步。

使用 **Kotlin + Jetpack Compose + Media3** 编写，原生运行，资源占用极少。

客户端有两个版本，功能相同，只是外观不同：

> `SoundCloud Full` - 桌面版风格的 Glass 设计，适合喜欢炫酷效果、手机性能强的用户。

> `SoundCloud Lite` - 早期版本使用的简化界面，适合喜欢简洁或手机性能较弱的用户。

### iOS 版已开始开发，可能很快完成

---

## 下载

前往 [发布页面](https://github.com/okeydw/SoundCloud-Android/releases/latest) 下载所需的 `.apk`：

| 文件 | 版本 |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud，Glass 设计 |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite，简洁界面 |

**安装：** 在手机上打开下载的文件，并允许安装未知来源的应用。之后可直接在应用内更新。

**系统要求：** Android 8.0 (Oreo) 或更高版本。

---

## 如何登录

接下来是登录，可以通过 SoundCloud，也可以扫描桌面版的二维码。

1. **桌面版二维码（推荐）。** 在 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 中打开 设置 -> 账号 -> 将会话转移到其他设备，然后扫描二维码。
2. **手机二维码。** 在登录界面点击"显示登录二维码"，在已登录的设备上扫描。
3. **通过浏览器。** "通过浏览器登录"按钮会打开普通的 SoundCloud 登录页。之后如果在别处登录，此会话可能会失效。

---

## 截图

> 背景色、强调色和文字颜色可通过 RGB 调色板修改，还可以添加背景图片和效果。

### SoundCloud (Full)

<p align="center">
<img src="../screenshots/full/wave.png" width="24%" />
<img src="../screenshots/full/player.png" width="24%" />
<img src="../screenshots/full/equalizer.png" width="24%" />
<img src="../screenshots/full/search.png" width="24%" />
</p>
<p align="center">
<img src="../screenshots/full/suggestions.png" width="24%" />
<img src="../screenshots/full/library.png" width="24%" />
<img src="../screenshots/full/playlists.png" width="24%" />
<img src="../screenshots/full/settings.png" width="24%" />
</p>

### SoundCloud Lite

<p align="center">
<img src="../screenshots/wave.png" width="24%" />
<img src="../screenshots/player.png" width="24%" />
<img src="../screenshots/player_immersive.png" width="24%" />
<img src="../screenshots/search.png" width="24%" />
</p>
<p align="center">
<img src="../screenshots/suggestions.png" width="24%" />
<img src="../screenshots/library.png" width="24%" />
<img src="../screenshots/history.png" width="24%" />
<img src="../screenshots/settings.png" width="24%" />
</p>

---

## 功能

### 音乐

- **音浪与推荐** - 基于点赞和收听历史的自研推荐系统
- **搜索** - 曲目、艺人、播放列表、专辑以及氛围搜索（神经网络根据上下文挑选曲目）
- **播放器** - 随机播放、循环、播放队列、歌词、喜欢与不喜欢
- **均衡器** - 内置预设，也可以创建自己的预设
- **播放列表** - 与 SoundCloud 同步的本地播放列表，可公开或私密，拖动即可调整曲目顺序并保存
- **历史与统计** - 收听时长、热门曲目和艺人、按天和按小时的图表
- **个人主页** - 关注与取消关注，SoundCloud 主页中的链接
- **SoundCloud Auto（测试版）** - 创建连接蓝牙设备时的场景

### 离线模式

- 下载曲目和播放列表，带缓存的离线模式
- 手动设置缓存上限和清理时间
- 同时安装两个版本时，Full 和 Lite 共享下载和播放列表（测试版，部分可用）

### 导入（测试版）

- **YouTube / YouTube Music** - 通过 Google 账号导入喜欢、播放列表和专辑
- **Yandex Music** - 通过令牌导入喜欢、播放列表和专辑
- **Spotify** - 通过 client_id 导入喜欢、播放列表和专辑

###### [了解更多](../import-lang/import.zh.md)

> 重要！！！我们搜索的是相似曲目，而不是导入音频本身
>（但这样可以绕过审查，fck censor）

---

## 反馈
社区: [Discord](https://discord.gg/Au3ebtfYu3)

发现了 bug 或有想法？最简单的方式是"设置 -> 关于系统 -> 支持"：在 GitHub 上提交 issue 或在 Discord 中创建工单，设备信息会自动填写。也可以手动 [提交 issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) 并附上日志："设置 -> 日志 -> 分享"。

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose, Material 3, Haze（Full 中的玻璃效果） |
| 音频 | Media3 (ExoPlayer + MediaSession)，带通知的后台服务 |
| 网络 | OkHttp（+ 磁盘缓存），kotlinx.serialization |
| 图片 | Coil（+ Palette 按封面取色） |
| 相机 | CameraX + ML Kit（Full 中的二维码扫描），ZXing |
| 存储 | SharedPreferences（设置、会话），下载和播放列表的 JSON 索引 |

后端: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

服务器状态请看 [这里](https://status.soundcloud-desktop.fun/)

**兼容性：** `minSdk 26` (Android 8.0)，`targetSdk 35`，`compileSdk 35`。

完整更新记录见 [CHANGELOG.md](../../CHANGELOG.md)。

---

## 开发者

**构建：** JDK 17，Android SDK 35。

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK 位于 app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # 单元测试
./gradlew lintLiteDebug                         # lint（baseline：./gradlew updateLintBaseline）
```

**源码：**

| 目录 | 内容 |
| --- | --- |
| `app/src/main` | 逻辑：API、播放器、下载、播放列表、导入 |
| `app/src/shared` | 通用界面和对话框（设置等） |
| `app/src/lite` | Lite 界面 |
| `app/src/full` | Full 的 Glass 界面 |

**发布：** 打上 `vX.Y.Z` 标签（须与 `versionName` 一致）后，会用同一个密钥构建并签名两个版本，并附上 `CHANGELOG.md` 中的说明发布。

**后端：**

| 域名 | 用途 |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | 音频 |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | 曲目存储 |
| `images.scnative.space` | 封面 |
| `pay.scnative.space` | STAR 支付 |
| `health.scnative.space` | 连接检测 |
| `call-*.scnative.space` | "用户网络" |
| `*.relay.scnative.space` | 备用线路 |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | 直接从 SoundCloud 下载曲目 |

二维码登录协议：桌面版仓库中的 [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md)。

---


## 许可证

MIT。详见 [LICENSE](../../LICENSE) 文件。

_SoundCloud 是 SoundCloud Ltd. 的商标。本应用与 SoundCloud 无关。_

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/下载_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="下载 SoundCloud Android"/>
</a>
</p>

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud 安卓</code> · <code>soundcloud 客户端</code> · <code>soundcloud 无广告</code> · <code>soundcloud 被封锁</code> · <code>soundcloud 打不开</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>soundcloud 音乐播放器</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/下载_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="下载 SoundCloud Android"/>
</a>
</p>
