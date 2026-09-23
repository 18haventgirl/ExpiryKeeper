# 进度记录（活文档，随开发增删改）

规格：`docs/superpowers/specs/2026-09-22-expiry-keeper-design.md`

## 当前状态

- [x] v2 品质升级（Task 1–13）全部完成 —— 2026-09-22 收尾：lint 0 error / 29 JVM 单测绿 / Room 迁移测试 emulator 通过 / 冷启动无崩溃
- [进行中] M1 单机可用 —— 代码与 v2 均在 emulator 跑通；MagicOS 真机杀后台验证仍待用户方便时进行
- 最后更新：2026-09-22

## 环境与构建备忘

- JDK：Android Studio 自带 JBR 25（`D:/Apps/AndroidStudio/jbr`），命令行用 `JAVA_HOME=... ./gradlew.bat`
- **AGP 版本被 Studio 上限锁定：本机 Studio(2026.1.3) 最高支持 AGP 9.3.0 系 → 项目锁 9.3.3，勿升到 9.4.x**（CLI 构建能过但 Studio 打开会报不兼容）
- SDK：`D:/Files/AndroidSDK`（platform android-37.0，build-tools 36.0.0），local.properties 已配
- Gradle：9.7.1 本地发行版 `D:/Files/gradle/`，wrapper 的 distributionUrl 指向本地 file:（避开下载抖动）
- 网络：直连 repo.maven.apache.org / plugins.gradle.org TLS 常被掐 → settings.gradle.kts 仓库顺序：华为云 → 阿里云 → repo1 → google → mavenCentral；gradle.properties 配了 127.0.0.1:7890 代理
- AGP 9 破坏性变更（9.4.1 实测）：`org.jetbrains.kotlin.android` 插件被禁止（Kotlin 编译内置）；**`org.jetbrains.kotlin.plugin.compose` 仍然必需**（compose=true 时）；`android.kotlinOptions` 与顶层 `kotlin{}` 均不可用，jvmTarget 由内置 Kotlin 跟随 compileOptions；KSP 插件单独保留

## 里程碑

### M1 单机可用
- [x] Gradle/AGP 脚手架（Kotlin 内置于 AGP 9.4 + Compose + Room + WorkManager，minSdk 29）
- [x] 数据层：items/categories/change_log 表，Repository（品类 M1 为代码内模板，未建表）
- [x] 提醒规则引擎（纯函数）+ JVM 单测 —— 9/9 绿
- [x] UI：添加 / 清单 / 今日 三屏（emulator 已跑通）
- [x] EXPIRY 本地通知代码（日扫 + 精确闹钟 + 权限请求）
- [ ] MagicOS 真机杀后台验证（emulator 无法验证，待用户方便时）

### v2 品质升级（2026-09-22 用户提出四条红线，见 spec §9）
- 计划：`docs/superpowers/plans/2026-09-22-expiry-keeper-v2-quality.md`（Task 1–13）
- [x] T1 git 纳管+CODESTYLE　[x] T2 包分层搬移　[x] T3 Room v2 迁移　[x] T4 引擎 v2　[x] T5 LWW 合并
- [x] T6 备份/恢复　[x] T7 设计系统　[x] T8 今日 v2　[x] T9 清单 v2　[x] T10 添加 v2
- [x] T11 详情+设置（2026-09-22）　[x] T12 通知 v2（2026-09-22）　[x] T13 全面体检（2026-09-22）

#### v2 验收记录（2026-09-22，Task 13 收尾）
- 设备：emulator-5554 · Pixel 9 (AVD) · Android 17 / API 37
- 静态：`lintDebug` **0 error**（1 个 NewApi error 已修：`canScheduleExactAlarms` 加 API 31 版本守卫）；16 warning + 1 hint 全部为低级/依赖版本类，逐条判断见 `task-13-report.md`，暂不改动
- 单测：`testDebugUnitTest` **32/32 绿**（ReminderEngine 14 / ExpiryForm 5 / SyncMerge 7 / Backup 6）
- 迁移（红线1 直接证据）：`connectedDebugAndroidTest` → `RoomMigrationTest.migrate1To2KeepsData` **通过**（v1 数据迁入 v2 新列、events 表建好、断言未删）。为跑通补了两处测试依赖修复：`androidx.test:runner` 缺失、`kotlinx-serialization` core/json 版本分裂（androidTest 强制对齐 1.8.1）
- 冷启动冒烟：`install -r` → force-stop → 冷启 → `topResumedActivity=com.expirykeeper/.MainActivity`，进程存活无崩溃；间隔 30s 两次 screencap 均为 1080×2424 非黑屏、渲染今日 v2 真实界面
- 泄漏粗检：`dumpsys meminfo` 两次采样（中间一次 HOME→重开切应用）TOTAL PSS ≈113MB、Native Heap 13.1→13.5MB（增幅 <20%），无增长趋势
- 全分支终审（v2 收尾）：review 38df8b9..1b1e106 → 修复波 093a1c0（13 文件）：importMerged 墓碑可见（防删除复活）、Backup.parse 严格化（缺时间戳/非法枚举/负值一律拒绝，+3 拒绝测试）、通知残留清扫、撤销 Snackbar replay=1、两周计数排除逾期、跨类型字段清理、续费周期内联校验、死代码清扫、snooze 边界修正、MigrationTest 改 JUnit 断言；复审判定 ADDRESSED
- 切页割裂感修复（2026-09-23，`297626d`）：用 uiautomator 逐页取 bounds 定位根因——① BigHeader 返回键与标题同行，把 displaySmall 标题在设置/添加页挤右 ~48dp（今日/清单贴左），改为返回键独立成行（M3 大标题式），各页标题左缘统一 x=42；② 页面横向边距 12dp（今日/清单/设置）与 16dp（添加）不一，全部统一 16dp；③ 设置页在 EkApp Scaffold 内又套一层 Scaffold，双吃系统栏 inset 使内容下沉，去掉内层；④ Hero 卡内边距 24dp 与列表卡 16dp 不齐，统一 16dp；⑤ 条目 emoji 26sp 撑出 44dp 圆底，降到 22sp；⑥ MainActivity 每次冷启都请求通知权限，已授权时回调触发 runNow→notifyAll 把用户刚滑掉的通知复活——改为仅未授权时请求。32/32 单测 + lint 0 error 复验通过
- 已知遗留（M3 起手清单）：① AddEdit 由 CONSUMABLE 改类时 quantity/unit/lowStockThreshold 残留（引擎按 reminderKind 分发，暂无行为影响）；② `ItemRepository.consumeOne` 无生产调用方（保留待 M2「吃完」快捷操作或后续删除）；③ Snackbar replay=1 撤销按钮二次点击会再写一次 updatedAt；④ `activeNotifications` 可加空防御；⑤ 备份不携带墓碑 → 恢复不复活删除，M3 WebDAV 同步需导出墓碑；⑥「每日提醒时间」设置项实际未接线（ReminderScheduler 固定 9 点）。M2 条码、M3 同步仍为未来里程碑
- 人工验收清单（脚本无法覆盖的 UI 手测项，源自 Task 6/8/9/10/11/12 brief）：

| 项 | 来源 | 手测步骤 | 通过标准 |
|---|---|---|---|
| 备份/恢复 | T6 | 设置→导出到「下载」→改一条数据→导入该文件 | Toast 成功且数据按 LWW 回滚正确 |
| 今日屏快速操作 | T8 | 造 3 条（明天到期/已过期/低库存）→点「今天不再提醒」/「稍后3天」/「续期」 | 分组与 DueRing 数字正确；处理/延后即时消失且重进不现；续期后到期日=today+shelfLife；暗色全页可读 |
| 清单搜索排序分组 | T9 | 搜「牛奶」实时过滤/清空恢复；切 4 种排序；看分组计数 | 无到期日者沉底；分组计数与明细一致；~30 条无 jank |
| 添加三步 & emoji | T10 | 「牛奶，开封 3 天」零键盘路径；自定义 🐠 保存；空名/双空规则校验；编辑 M1 旧数据 | ≤15 秒完成；emoji 在今日/清单/详情/通知标题均显示；非法输入按钮禁用且提示明确；旧数据不丢字段不崩 |
| 详情/设置/撤销 | T11 | 长按→详情浮层；删除→Snackbar 撤销；权限卡两行；动态色开关；概览计数 | 详情数据正确；撤销后物品回归且到期日不变；权限状态真实反映；开关即时变色/回落 teal；概览与清点一致 |
| 通知聚合与延后 | T12 | 造 4 条到期→看通知栏分组摘要→点「稍后 3 天」 | 聚合成组、点开列表正确；延后后该通知消失且今日屏同步消失 |

### M2 品类完备
- [ ] CONSUMABLE / RECURRING 语义 + 品类模板与保质期常识库
- [ ] 条码扫描（MLKit）+ 可选在线查询
- [ ] 多档提醒偏移、通知渠道分组
- 验收：6 品类各录 3 件真实物品，全家桶提醒正确

### M3 同步协议
- [ ] change_log 增量导出 / LWW 合并 / 墓碑清理
- [ ] SyncDriver 接口 + LocalFolderDriver（双目录仿真两设备）
- [ ] 冲突仿真测试（并发编辑、删除复活、离线回归）
- 验收：两台设备（仿真）数据最终一致，无丢改

### M4 家庭共享
- [ ] WebDavDriver（坚果云 / NAS）
- [ ] 二维码配对导入同步配置
- [ ] 成员标识（deviceId → 昵称）、全家通知
- 验收：家人手机扫码入伙，妈妈添加的物品我手机能收到提醒

### M5 打磨
- [ ] 物品照片、月度统计（丢弃成本）、桌面小组件/图标角标

## 决策与发现日志

- 2026-09-20 放弃 token 统计方向：开源过于成熟（codeburn/tokscale/aiusage 等，全 MIT）。调研成果保留在 `reference/`（4 个克隆仓库），与本项目无关，可删。
- 2026-09-22 立项"到期管家"：纯自用、家庭共享、本地优先零服务器、同步走 SyncDriver 接口（LocalFolder → WebDAV）、提醒全本地生成。

## 风险雷达

| 风险 | 状态 |
|---|---|
| 录入摩擦导致弃用 | M1 先用起来验证，模板把添加压到 15 秒内 |
| MagicOS 杀后台导致通知不准时 | M1 真机验证；备选：充电时补扫 + 打开 App 时补发 |
| 中文条码库 API 不可用 | M2 前验证，降级纯手动 |
| 目录名仍叫 MyTokens | 待用户确认是否改目录/工程名 |
