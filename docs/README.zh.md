<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
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
<img src="icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">桌面版</a>]</b><br>
<b>非官方 SoundCloud Android 客户端</b><br>
无广告 · 无验证码 · 无审查
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-Android?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-Android/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-Android/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
</a>

<a href="../LICENSE">
<img src="https://img.shields.io/badge/License-MIT-0048FF?style=for-the-badge" alt="License"/>
</a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/下载_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="下载 APK"/>
</a>
</p>

---

## 这是什么？

**SoundCloud Android** 是一款手机上的原生 SoundCloud 客户端。它使用与 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 相同的后端，因此可以直接访问完整的曲库。

使用 **Kotlin + Jetpack Compose + Media3** 编写，原生运行，资源占用极低。

客户端有两个版本，功能相同，只是外观不同：

> `SoundCloud Full` - 仿照桌面版的 Glass 设计：玻璃效果、光晕、动画，以及独立的界面和播放器。

> `SoundCloud Lite` - 熟悉的简洁界面，适合喜欢极简风格和配置较低手机的用户。

### iOS 版本已开始规划，可能很快就会推出

---

## 下载

前往 [发布页面](https://github.com/okeydw/SoundCloud-Android/releases/latest)，下载你需要的 `.apk`：

| 文件 | 版本 |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud，Glass 设计 |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite，简洁界面 |

**安装:** 在手机上打开下载的文件，并允许安装未知来源的应用。之后可以直接在应用内更新。

**系统要求:** Android 8.0 (Oreo) 或更高版本。

---

## 如何登录

不登录的话，应用只能停留在登录界面（离线模式下播放已下载的歌曲除外）。登录方式：

1. **从桌面版扫码（推荐）。** 在 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 中打开“将会话转移到其他设备”，然后在手机上点击“打开相机”，用内置扫描器扫描二维码。
2. **从手机扫码。** 在登录界面点击“显示登录二维码”，然后在已登录的设备上扫描。
3. **在浏览器中登录。** “通过浏览器登录”按钮会打开常规的 SoundCloud 登录页面。如果之后在其他地方登录，这个会话可能会失效。

---

## 截图

> 背景、文字颜色和主题都可以自定义：自己的背景图片、RGB 调色板和现成的主题。

### SoundCloud (Full)

<p align="center">
<img src="screenshots/full/wave.png" width="24%" />
<img src="screenshots/full/player.png" width="24%" />
<img src="screenshots/full/equalizer.png" width="24%" />
<img src="screenshots/full/search.png" width="24%" />
</p>
<p align="center">
<img src="screenshots/full/suggestions.png" width="24%" />
<img src="screenshots/full/library.png" width="24%" />
<img src="screenshots/full/playlists.png" width="24%" />
<img src="screenshots/full/settings.png" width="24%" />
</p>

### SoundCloud Lite

<p align="center">
<img src="screenshots/wave.png" width="24%" />
<img src="screenshots/player.png" width="24%" />
<img src="screenshots/player_immersive.png" width="24%" />
<img src="screenshots/search.png" width="24%" />
</p>
<p align="center">
<img src="screenshots/suggestions.png" width="24%" />
<img src="screenshots/library.png" width="24%" />
<img src="screenshots/history.png" width="24%" />
<img src="screenshots/settings.png" width="24%" />
</p>

---

## 功能

除非另有说明，以下功能两个版本都有。

### 音乐

- **浪潮与推荐** - 个人信息流、推荐栏和精选，基于歌曲或艺人的电台
- **搜索** - 歌曲、艺人、播放列表、专辑，以及氛围搜索（用文字描述心情，测试版）
- **播放器** - 后台播放、通知栏控制、波形图、随机/循环、手势、歌词（如有则同步显示）
- **平滑过渡** - 歌曲之间的淡入淡出，时长 1 到 30 秒
- **均衡器** - 系统预设和自定义预设，不需要的标准预设可以隐藏
- **音频输出** - 可选扬声器、有线耳机、蓝牙或 USB
- **播放队列** - 在任意歌曲菜单中选择“下一首播放”

### 音乐库

- **喜欢与不喜欢** - 不喜欢的歌曲会被跳过，并有单独的列表
- **播放列表** - 自己的本地播放列表，可同步到 SoundCloud，公开或私密，自定义封面，拖动即可调整顺序并会保存
- **历史与统计** - 收听时长、热门歌曲和艺人、按天和按小时的图表
- **艺人卡片** - 简介、社交网络、专辑和相似艺人
- **个人主页** - 关注和取消关注，SoundCloud 主页中的链接
- **屏蔽艺人** - 类似“不喜欢”，但针对整个艺人：不会出现在浪潮、电台和推荐中，列表与 Desktop 共享

### 离线与下载

- 下载单曲和整个播放列表，带缓存的离线模式
- 缓存容量和保留时长限制，提升已下载内容的音质（Star 用户）
- 同时安装 Full 和 Lite 时共享下载和播放列表（测试版，部分情况下可用）

### 导入（测试版）

- **YouTube / YouTube Music** - 通过链接导入单曲或播放列表，音频和封面直接从 YouTube 获取，可以立即创建播放列表（在俄罗斯需要 VPN）
- **Yandex Music** - 通过令牌或在应用内登录 Yandex，导入喜欢、播放列表和专辑
- **Spotify** - 通过链接导入播放列表、专辑和单曲

对于 Yandex Music 和 Spotify，应用会 **在 SoundCloud 上查找相似的歌曲，而不是转移原曲**。找到的歌曲可以立即加入下载。
（但能绕过审查，fck censoor）
### SoundCloud Auto (测试版)

- 手机通过蓝牙连接汽车时自动开始播放音乐
- 可选择音源（喜欢、浪潮、下载、播放列表）、设备和随机播放
- 为汽车单独设置均衡器预设，断开连接后恢复之前的预设

### 网络

- 多个主机和中继节点，节点自动发现
- 手动选择节点，显示延迟并进行 64 KB 测试：可以看出运营商是否在 13 KB 后切断连接，自动模式不会选择这样的节点
- “无网络/服务器不可用”提示只在断开连接 15 秒后才会出现

### 外观

- 9 种语言
- 主题、自定义背景（带模糊和暗化的图片）、强调色和文字颜色、RGB 调色板、主题导出和导入
- 应用图标：紫色、蓝色或橙色（自定义图片会作为单独的快捷方式添加，这是 Android 的限制）
- **仅 Full:** Glass 界面、Star 用户的个人主页光晕（与 Desktop 同步）、沉浸式播放器、设置平滑打开

### 其他

- 自动更新：点击新版本提示，APK 会自动下载并打开安装程序
- 通过二维码登录（直接在应用内使用相机）、通过链接或在浏览器中登录
- 在账户中显示已绑定的 Discord
- “关于”中的支持：GitHub issue 或 Discord 工单，设备信息自动填写

---

## 反馈

发现了 bug 或有想法？最简单的方式是“设置 -> 关于 -> 支持”：会打开带有设备信息的 GitHub issue 或 Discord 工单。也可以手动 [提交 issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) 并附上日志：“设置 -> 日志 -> 分享”。

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose, Material 3, Haze（Full 中的玻璃效果） |
| 音频 | Media3 (ExoPlayer + MediaSession)，带通知的后台服务 |
| 网络 | OkHttp（+ 磁盘缓存），kotlinx.serialization |
| 图片 | Coil（+ Palette 根据封面取色） |
| 相机 | CameraX + ML Kit（Full 中的二维码扫描器），ZXing |
| 存储 | SharedPreferences（设置、会话），下载和播放列表的 JSON 索引 |

与 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 使用相同的后端：应用只是它的另一个客户端。

**兼容性:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

完整的更新记录见 [CHANGELOG.md](../CHANGELOG.md)。

---

## 开发者

**构建:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK 位于 app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # 单元测试
./gradlew lintLiteDebug                         # lint（基线：./gradlew updateLintBaseline）
```

**源码:**

| 目录 | 内容 |
| --- | --- |
| `app/src/main` | 逻辑：API、播放器、下载、播放列表、导入 |
| `app/src/shared` | 通用界面和对话框（设置等） |
| `app/src/lite` | Lite 界面 |
| `app/src/full` | Full 的 Glass 界面 |

**发布:** 推送 `vX.Y.Z` 标签（须与 `versionName` 一致）会用同一个密钥构建并签名两个版本，并附上 `CHANGELOG.md` 中的说明发布。

**后端:**

| 用途 | 主机 |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| 流媒体 | `stream.scnative.space`, `stream-star.scnative.space` |
| 存储 | `storage.scnative.space`, `storage-star.scnative.space` |
| 图片 | `images.scnative.space` |
| 中继节点 | `*.rN.relay.scnative.space`, 列表: `health.scnative.space/topology` |

二维码登录协议：桌面版仓库中的 [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md)。

---

## 数据与信任

- 应用通过 `*.scnative.space` 后端工作（与桌面版相同）。**SoundCloud 会话和令牌保存在该后端**，手机上只保存会话 ID。
- “退出登录”会删除后端的会话（`POST /auth/logout`）以及手机上的所有数据。
- 直连 SoundCloud 的备用通道（可在设置中关闭）访问 soundcloud.com 时不携带你的会话。
- 用于导入的 Yandex Music 令牌只保存在手机上。应用永远看不到你的 Yandex 密码：登录在 Yandex 自己的页面上完成。

---

## 许可证

MIT。详情见 [LICENSE](../LICENSE) 文件。

_SoundCloud 是 SoundCloud Ltd. 的商标。本应用与 SoundCloud 无关联。_

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud 安卓</code> · <code>soundcloud 客户端</code> · <code>soundcloud 无广告</code> · <code>soundcloud 被封锁</code> · <code>soundcloud 打不开</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>soundcloud 音乐播放器</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/下载_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="下载 SoundCloud Android"/>
</a>
</p>
