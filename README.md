# Android 16 Bilibili

一个基于 Jetpack Compose 的 Android Bilibili 客户端实验项目，当前已实现首页、关注、直播、历史、搜索、用户主页、视频详情与番剧/合集播放等主要能力。

## 当前功能

- 首页推荐、关注动态、排行榜、历史、收藏
- 视频详情播放、分 P 切换、合集切换、评论浏览
- 番剧搜索与番剧播放
- 直播推荐、关注直播、直播间播放、在线人数刷新
- 直播间弹幕显示、弹幕表情、清屏手势、主播主页跳转
- 用户主页、关注/粉丝列表、资料缓存
- 设置页内置使用说明，以及信息流单列/双列切换

## 运行环境

- Android Studio 最新稳定版
- JDK 11
- Android SDK 37
- 最低系统版本：Android 16

## 本地运行

1. 使用 Android Studio 打开项目根目录 `bilibili`
2. 确认本地已安装 Android SDK 37
3. 如需 release 签名，准备 `keystore.properties`
4. 直接运行 `app` 模块

Debug 编译命令：

```bash
./gradlew :app:assembleDebug
```

## Release 打包

项目已在 `app/build.gradle.kts` 中读取 `keystore.properties` 作为 release 签名配置。

`keystore.properties` 示例：

```properties
storeFile=release-keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

打包命令：

```bash
./gradlew :app:assembleRelease
```

默认输出位置：

```text
app/build/outputs/apk/release/app-release.apk
```

## 使用说明摘要

- 首页、关注、历史支持下拉刷新
- 再次点击当前底部 Tab 会回到顶部并触发刷新
- 视频详情支持分 P、合集、番剧切换
- 直播间支持全屏、手动刷新、弹幕设置、右滑清屏/左滑恢复
- 直播间左上角主播头像和昵称支持进入个人主页
- 设置页中可查看更完整的应用内使用说明

## 当前状态

这是一个持续迭代中的个人项目，接口依赖 Bilibili 网页侧能力；如果上游接口变化，部分页面或功能可能暂时失效。
