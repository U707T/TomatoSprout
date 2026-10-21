# 番茄芽 TomatoSprout 🍅

轻量打卡 + 番茄专注 + 数据回顾。原生 Android（Kotlin + Jetpack Compose），**不索权、不打扰、不空洞**。

> 只申请 3 个权限：`POST_NOTIFICATIONS`（可选）、`INTERNET`、`ACCESS_NETWORK_STATE`。
> 没有使用情况监听、没有无障碍、没有悬浮窗、没有桌面小组件、没有托管后端。

## 功能

- **今日打卡**：习惯 / 待办 / 目标 / 番茄任务，一键打卡与撤销，连续天数，简约模式，心情与备注。
- **日历与统计**：月/年 Canvas 热力图、本周纵览、周/月完成率、连续纪录、历史时间轴（按任务与日期筛选）、补卡（最近 7 天）。
- **番茄专注**：倒计时/正计时、5–180 分钟、严格模式、退后台按真实时间校正、午夜归属、专注记录。
- **实时活动卡片**：`NotificationCompat` + 自定义 `RemoteViews` 的胶囊通知，可暂停/继续/结束；拒绝通知权限时静默关闭。
- **成就徽章**：连续 7/30/100 天、100 次打卡、100 小时专注、50 个番茄、圆满日等，解锁有轻量缩放淡入动效。
- **数据资产**：JSON 全量备份 / CSV 打卡导出 / JSON 导入（字段校验 + 冲突提示）。
- **WebDAV**：手动双向同步、哈希 + ETag 比对、只增不删、冲突副本保留、同步日志、仅 Wi-Fi、失败最多重试 3 次。
- 深色模式、系统字体缩放、TalkBack 基础标签、48dp 点击区域。

## 权限

| 权限 | 用途 | 可拒绝 |
|---|---|---|
| `POST_NOTIFICATIONS` | 习惯提醒 / 专注实时活动 | 是，静默降级 |
| `INTERNET` | 仅手动 WebDAV 同步 | — |
| `ACCESS_NETWORK_STATE` | 仅 Wi-Fi 同步判断 | — |

WorkManager 传递引入的 `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED` / `FOREGROUND_SERVICE` 已在 Manifest 中显式移除。文件导出/导入使用 SAF，不申请存储权限。

## 工程结构

```
:app                 UI（Compose）/ ViewModel / Room / DataStore / WebDAV / 通知 / 备份
:core:domain         纯 Kotlin：领域模型、计时状态机、连续天数、徽章、统计、UseCase
```

> 说明：提示词的模块清单允许小团队先合并为 `:app + :core + :feature`。本项目进一步收敛为
> `:app + :core:domain`，UI / data / feature 以包边界隔离（`ui/`、`data/`、`sync/`、`notify/`）。
> Domain 不依赖 Android SDK，可在 JVM 上直接测试。

## 构建与测试

沙箱 / 本地：

```bash
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
./gradlew :core:domain:test          # 23 个纯 JVM 单测
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

发布包由 GitHub Actions 构建并签名；push `main` 后自动上传 Release。签名材料只在本地/Secrets，不进仓库。

## 设计取舍

- **提醒**：用 `AlarmManager.setAndAllowWhileIdle`，不申请 `SCHEDULE_EXACT_ALARM`，因此可能延迟几分钟；未申请 `RECEIVE_BOOT_COMPLETED`，重启后需打开 App 恢复排期。
- **计时**：基于 `SystemClock.elapsedRealtime()` 的单调时钟；回前台按真实流逝校正，进程被杀后从 DataStore/SharedPreferences 快照恢复。若进程在计时中死亡，通知会停留在最后一次更新，直到回到 App 结算；不申请前台服务常驻。
- **实时活动**：视觉形态取决于 ROM 对 Heads-Up 通知的渲染，App 内提供了标准降级外观。通知按钮使用 Activity PendingIntent，规避 Android 12+ 的通知 trampoline 限制。
- **WebDAV**：只支持用户自填服务器，无任何托管后端 SDK；未配置时零网络请求。

## 隐私

不收集任何用户数据、无埋点、无广告、无账号。数据默认仅在本机；WebDAV 密码使用 Android Keystore AES-GCM 加密，不写入数据库、日志或同步文件。

详细对照见 [docs/功能对照清单.md](docs/功能对照清单.md)。
