# 番茄芽 TomatoSprout 🍅

轻量打卡 + 番茄专注 + 数据回顾。原生 Android（Kotlin + Jetpack Compose），**不索权、不打扰、不空洞**。

> 只申请 4 个权限：`POST_NOTIFICATIONS`（可选）、`INTERNET`、`ACCESS_NETWORK_STATE`、`RECEIVE_BOOT_COMPLETED`（重启恢复本地提醒）。
> 没有使用情况监听、没有无障碍、没有悬浮窗、没有桌面小组件、没有托管后端。

## 功能

- **任务卡片流**：习惯 / 待办 / 目标 / 番茄任务，大卡片展示；**不计时（按次）任务** 可直接打卡（多目标逐次累加、确认撤销）；**计时任务只显示「开始」**，进入专注页完成后自动打卡。
- **专注 × 打卡闭环**：倒计时自然结束自动写入专注记录与打卡；正计时结束确认；不足 30 秒按设置丢弃；手动停止只记录中断、不打卡；严格模式彻底作废。
- **跨午夜**：默认归属结束日；开启「午夜模式」后强制归属开始日；每条专注保存 `attributedDate`，统计与历史一致。
- **历史时间轴（二级页）**：月历 + 选中日时间轴；点击记录写心得；最近 7 天补卡并标记 `isMakeup`。
- **统计**：日 / 周 / 月环形分布、月度柱状图、连续纪录、周/月完成率、月/年 Canvas 热力图；空数据与异常时长均有兜底。
- **删除任务**：取消归档；二次确认后软删除，打卡与专注历史保留，显示为「[已删除] 原任务名」并置灰。
- **番茄专注**：倒计时/正计时、5–180 分钟、严格模式、退后台按真实时间校正、实时活动卡片、屏幕常亮 / 亮度 / 本地白噪音 / 分享海报。
- **成就徽章**：连续 7/30/100 天、100 次打卡、100 小时专注、50 个番茄、圆满日等，解锁有轻量缩放淡入动效。
- **数据资产**：JSON 全量备份 / CSV 打卡导出 / JSON 导入（字段校验 + 冲突提示）。
- **WebDAV**：手动双向同步、哈希 + ETag 比对、只增不删、冲突副本保留、同步日志、仅 Wi-Fi、失败最多重试 3 次。
- 深色模式（DarkGray 背景 / LightGray 文字）、系统字体缩放、TalkBack 基础标签、48dp 点击区域。

## 权限

| 权限 | 用途 | 可拒绝 |
|---|---|---|
| `POST_NOTIFICATIONS` | 习惯提醒 / 专注实时活动 | 是，静默降级 |
| `INTERNET` | 仅手动 WebDAV 同步 | — |
| `ACCESS_NETWORK_STATE` | 仅 Wi-Fi 同步判断 | — |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复本地提醒闹钟 | — |

WorkManager 传递引入的 `WAKE_LOCK` / `FOREGROUND_SERVICE` 已在 Manifest 中显式移除；`RECEIVE_BOOT_COMPLETED` 仅用于重启恢复本地提醒。文件导出/导入使用 SAF，不申请存储权限。

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
./gradlew :core:domain:test          # 24 个纯 JVM 单测
./gradlew :app:lintDebug             # 静态检查（0 error）
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

发布包由 GitHub Actions 构建并签名；push `main` 后自动上传 Release。签名材料只在本地/Secrets，不进仓库。

## v1.6 UI 结构

- 底栏：任务 / 统计 / 我的（仅 3 个 Tab）
- 任务页：大卡片流 + 快捷添加 + 长按毛玻璃快捷面板；按次任务直接打卡，计时任务只显示「开始」
- 历史页：二级路由，月历 + 当日时间轴；从统计页或任务长按面板进入
- 专注页：全屏沉浸计时（无底栏），退出有淡入淡出过渡；状态跨页面由 SharedPreferences 快照恢复
- 统计页：环形分布 + 月度柱状图 + 热力图；点击图表进入历史页

## 设计取舍

- **提醒**：用 `AlarmManager.setAndAllowWhileIdle`，不申请 `SCHEDULE_EXACT_ALARM`，因此可能延迟几分钟；已申请 `RECEIVE_BOOT_COMPLETED`，开机/应用更新后由 BootReceiver 重排。
- **计时**：基于 `SystemClock.elapsedRealtime()` 的单调时钟；回前台按真实流逝校正，进程被杀后从 DataStore/SharedPreferences 快照恢复。若进程在计时中死亡，通知会停留在最后一次更新，直到回到 App 结算；不申请前台服务常驻。
- **实时活动**：视觉形态取决于 ROM 对 Heads-Up 通知的渲染，App 内提供了标准降级外观。通知按钮使用 Activity PendingIntent，规避 Android 12+ 的通知 trampoline 限制。
- **WebDAV**：只支持用户自填服务器，无任何托管后端 SDK；未配置时零网络请求。

## 隐私

不收集任何用户数据、无埋点、无广告、无账号。数据默认仅在本机；WebDAV 密码使用 Android Keystore AES-GCM 加密，不写入数据库、日志或同步文件。

详细对照见 [docs/功能对照清单.md](docs/功能对照清单.md)。
