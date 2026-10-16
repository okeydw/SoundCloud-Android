<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-0048FF?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

# 导入音乐

导入功能位于 **设置 -> 导入（测试版）**。
工作方式：

- **Spotify 和 Yandex Music。** 应用不会复制原曲，而是按曲名和艺人在 SoundCloud 上查找相同的曲目。
- **YouTube。** 可以直接从 YouTube 获取曲目，也可以在 SoundCloud 上查找相似曲目。
- 直接从 YouTube 获取的曲目和播放列表不会与 SoundCloud 同步
- 如果播放列表超过 500 首曲目，会拆分成多个播放列表
- 令牌和会话只保存在手机上。

## 通过链接

1. 复制曲目、播放列表或专辑的链接。
2. 粘贴到"通过链接导入"输入框中。
3. 选择要添加到哪里，然后点击"导入"。

不登录也可以导入公开链接。私密播放列表和喜欢的曲目需要在下方连接账号。
> Spotify！
> 不登录时，只能读取播放列表中的前 100 首曲目。


## Spotify

1. 打开 [Spotify 开发者控制台](https://developer.spotify.com/dashboard) 并登录你的账号。
2. 点击 **Create app**。名称和描述随意。
3. 在 **Redirect URIs** 中添加应用版本，可以两个都加，但要分开添加：
   - Lite：`com.scd.android://spotify-auth`
   - Full：`com.scd.android.full://spotify-auth`
4. 在 **Which API/SDKs are you planning to use?** 中勾选 **Web API** 并保存。
5. 打开 **Settings -> User Management**，添加要导入的 Spotify 账号的邮箱。
6. 复制 **Client ID**（32 位字母和数字），粘贴到应用中：**导入 -> Spotify -> Client ID**。
7. 按钮会变成"授权应用"，点击后在授权窗口中点击确认。

如果一切正确，会显示你的 Spotify 名称和头像，之后就可以导入喜欢的曲目和私密播放列表。

注意：

- 处于开发模式的 Spotify 应用只对 User Management 中的账号有效。控制台中的应用所有者需要 Spotify Premium，否则 Spotify 会返回 403。
- 如果 Spotify 拒绝了 Client ID，输入框下方会显示错误：请在控制台中检查 ID 和 Redirect URI。

如果输入框为空，"登录 Spotify"按钮会打开内置登录窗口。这样更简单，但不一定总能成功。

## YouTube

1. 打开 **导入 -> YouTube (Music)**，点击 **添加账号**。
2. 在弹出的窗口中登录 Google。窗口会自动关闭，并显示账号名称和头像。
3. **导入收藏** 会在 SoundCloud 上查找你点赞过的视频，并整理成播放列表。

可以通过链接导入曲目或播放列表，有两种模式：

- **从 YouTube 获取。** 音频从 YouTube 下载，播放列表只保存在手机上。
- **在 SoundCloud 上查找相似曲目。** 在 SoundCloud 上查找相同的曲目。

如果 Google 提示浏览器不安全，请使用 VPN。

## Yandex Music

1. 打开 **导入 -> Yandex Music**，点击 **使用 Yandex 登录**。
2. 会打开浏览器，选择需要的账号。
3. 复制跳转后页面的地址，回到应用并点击"粘贴链接"按钮。
4. **导入收藏** 会迁移你喜欢的曲目。

也有自动登录，可以试试。

要切换账号，点击 **移除账号** 后重新登录。
