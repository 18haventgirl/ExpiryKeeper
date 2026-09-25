# 进度记录（活文档，随开发增删改）

规格：`docs/superpowers/specs/2026-09-22-expiry-keeper-design.md`

## 当前状态

- [x] v2 品质升级（Task 1–13）全部完成 —— 2026-09-22 收尾：lint 0 error / 29 JVM 单测绿 / Room 迁移测试 emulator 通过 / 冷启动无崩溃
- [x] M1 代码与 UI 在 emulator 全部跑通；M2 常识库模板上线（录入缩到两次点击）；UI 审计四批整改完成
- [x] 备份/恢复语义重做（2026-09-25 用户验收发现的缺陷）：恢复从"LWW 合并"改为"整库时间点还原"，带确认框、一次性撤销、墓碑随备份走
- [x] 订阅过期不再静默（新增「续费逾期」状态，与 EXPIRY 逾期同构）；「每日提醒时间」真正接线（新增设置卡，兜底路径过时刻闸）
- [ ] **待用户**：10 项人工验收清单（见下表；恢复 3 项、订阅逾期 1 项、提醒时间 1 项为近期新增）+ MagicOS 真机杀后台验证
- [~] M3 同步 / M4 家庭共享 / M2 条码 —— **全部取消**（2026-09-25：没有服务器就不做多人；条码没有可用中文数据源）
- 余下可做：M5 打磨 + 小遗留清扫（见「已知遗留」①②③④）
- 分支：`dev/ui`（已推 origin，尚未并回 `main`）。最后更新：2026-09-25

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
- 分支与远端（2026-09-23）：远端 `github.com/18haventgirl/ExpiryKeeper`，默认分支 `main`。v2 全部工作经 **PR #1**（`v2-quality` → `main`，merge commit `b8a86fc`，22 提交 / 60 文件 / +3754 −716，合并后与 `v2-quality` 的树逐字节相同）并入主干；此后从 `main` 开新分支开发，`v2-quality` 仅作历史留档未删除。仓库只含源码：release 未配置签名（无 keystore），故无 APK/AAB 产物
- 度量口径订正（2026-09-24）：emulator-5554 实际是 **420dpi / density 2.625**，不是 3.0（实测 `padding(horizontal=16.dp)` → 左缘 42px、`IconButton 48.dp` → 126px）。此前按 px/3 换算的 dp 值全部偏小 12.5%，px 级证据与结论不受影响
- 清单搜索栏两处缺陷修复（2026-09-23，合并后首次复测发现）：M3 `SearchBar` 收起态会对 `SearchBarDefaults.windowInsets`（= systemBars Top+Horizontal）做 `windowInsetsPadding`，而 `EkApp` 的 Scaffold 只 pad 不 consume，状态栏 142px 被垫第二遍 → 标题与搜索框之间 184px（≈70dp）死空白；更严重的是 `SearchBar` 激活时展开的结果槽位我们传的是空 `{}`，实测输入 `LED` 时计数显示「1 件」但屏幕渲染 0 行（匹配结果被空面板盖住），搜索在 UI 上等于不可用。改为不展开的 `SearchBarDefaults.InputField`（实时过滤下方列表本就是 Task 9 的设计意图），两个问题一并消除，并接上 IME 搜索键收起键盘。复测：副标题→搜索框间距 184px→63px，首卡上移正好 142px，`LED` 查询渲染出「LED灯泡」行、清空恢复 6 件、32/32 单测 + lint 0 error 无回归
- UI 审计与四批整改（2026-09-24，分支 `dev/ui` 已推送，详见 `docs/UI-AUDIT.md`）：三路并行取证（代码审查 / 模拟器量化 / GitHub 参考）后，第 1 批修正确性（逾期天数可见、今日「即将到期」与 hero 同源、表单首帧不报红、加载态），第 2 批修结构（详情浮层改根层覆盖、次级页去底栏、清单尾部语义分离、edge-to-edge），第 3 批做质感（tonal 分层去阴影、CountPill 取代红 Badge、排版 role、动效、搜索图标与清除、空态居中、功能 emoji→Material 图标、分隔符统一、长按去重、反馈走 Snackbar），第 4 批资产化（dateZh 统一四处日期、VM 私有状态+setter 让直写变成编译错误、EkCard/Pill/KeyValueRow 收敛四份重复卡片、chip 触控区扩至 48dp、Hero FlowRow 抗大字号）。单测 32 → 49，lint warning 16 → 13、0 error
  - 过程中三次自我纠错并留档：`dialog()` 路由因 Compose 1.12.1 无 `dimAmount` 造成双重压暗而否决；`isLoading` 极性写反导致空态被永久压制（单变量实验定位）；**负 padding 让设置/添加一点即崩**，被用户当场发现 —— 单测与 lint 结构上都抓不到「某屏一进去就崩」，故新增 `scripts/smoke-routes.sh` 全路由冒烟纳入门禁
- 模拟器纪律与种子数据（2026-09-24）：Studio 里 app 打开后清单空空如也，追查为**开发工具混代**——CLI 用 `-no-snapshot-save` 启同一台 AVD（写盘在退出时被丢弃），Studio 又载入 9 月 22 日的 `default_boot` 快照把磁盘回滚，于是 `expiry-keeper.db`（只有 schema）与 `-wal`（唯一装着数据的帧）来自不同世代，SQLite 判定 WAL 无效直接重置，数据静默消失。**应用侧无 bug**：Room 未开 `fallbackToDestructiveMigration`，logcat 无异常，卸载重装才干净。取证后该快照已被今天的状态覆盖。三条纪律：① 不再用 `run-as sqlite3` 直改数据库（写 WAL 之外的世代是本次元凶之一，且只读打开也会顺带 checkpoint 掉 WAL，毁掉最后一个可恢复物证）；② 需要两台设备时另建 AVD，不与 Studio 抢 `Pixel_9`；③ 造数据走 `SeedDataTest`——在目标进程内经 `ItemRepository.save()`，派生到期日、change_log、events 全都真实生成，id 固定故重复执行是 upsert。7 条种子覆盖逾期/临期/续费今天/低库存/窗口外。注意 `connectedDebugAndroidTest` 跑完会回滚安装（连数据目录一起删），所以种完数据要用 `adb install -r` + `am instrument`，验证完再种一次即可
- 备份恢复语义重做（2026-09-25，用户验收报告的缺陷）：导出→改一条→导入**恢复不回来**。根因不是某个比较符，而是**职责错配**——恢复走的 `SyncMerge` 是 v2 T5 为 M3 同步写的 last-writer-wins 合并（文件注释还写着"M3 与备份共用"），T6 顺手拿去做恢复；而 LWW 恰恰是合并语义：改过的行 `updatedAt` 永远比备份新（`save()` 每次都刷成 now），于是备份被当作过期数据整条拒收。M3 已取消，该函数唯一调用方就是恢复，保留合并语义等于永久错。
  - 改为**时间点还原**：`core/domain/Restore.kt` 的 `plan(local, incoming)` 出计划（写回 N 条 / 重放 K 条删除 / 移出 M 件），`ItemsViewModel` 拆成 `inspectBackup`（只读）+ `applyRestore`（确认后才写），设置页加确认 `AlertDialog` 把账目摊开，恢复后 Snackbar 一次性「撤销」可整库换回。空备份拒绝（否则等于清空清单）
  - 用户数据行**永不物理删除**：备份里没有的那些只打墓碑，所以"移出"是可逆的；清空+写入放进 `ItemDao.replaceWith` 的单个事务，中途崩溃不会留下半份清单
  - 导出改用 `getAllIncludingTombstones()`：备份现在保住"删除发生的时间"，恢复后墓碑行的 `deletedAt/updatedAt` 是当初真删那一刻而非恢复那一刻
  - 删掉 `SyncMerge.kt` + `SyncMergeTest.kt`（8 条测试由 `RestoreTest` 7 条接替），并清掉随之失去调用方的 `upsertRaw` / `changeLogSince`
  - 证据：`RestoreTest` 7/7 绿；新增 `RestoreDbTest` 在 in-memory Room 上真跑三件事（旧版本确实盖掉本地更新 / 备份后新增的物品只留墓碑不物理删 / 撤销能换回），2/2 绿；`RoomMigrationTest` 复绿；单测 55 全绿、lint 无新增告警
  - 真机走查（emulator-5554，`.debug-ui/audit/verify5.js`）：导出→Downloads 里的文件确实带墓碑（你验收时删掉的那盒鲜牛奶以 `deletedAt` 形式在文件里）→ 今日屏「今天不再提醒」处理掉一条（待处理 3→2）→ 导入同一份备份 → 确认框报「写回 7 条物品，重放 1 条删除」→ 替换全部 → 提示条「已恢复：写回 7 条，移出 0 条」→ 点「撤销」→ 回执「已撤销，清单回到恢复之前」，待处理回到 2 = 改动被带回来。**恢复与撤销两条路都肉眼验过**
  - 过程中我自己两处测试错误（不是 App 问题，记下来防重犯）：用 `label.includes("替换全部")` 找按钮会先命中标题「用这份备份替换全部物品？」，于是"点了三次没反应"其实点的是标题——按钮一律精确匹配；以及第二轮拿同一份内容再导一次，恢复前后数据相同，这种跑法根本测不出撤销，必须先拉开差距（A≠B）再判 D==B
- 订阅过期完全静默已修（2026-09-25，我在恢复验证过程中发现、用户拍板修）：`ReminderEngine.recurring()` 只认 `daysLeft == 0` 与 `1..7 且命中偏移`，**扣费日一过就落到 `else → null`**，于是「视频会员」过期 300 天也不报也不提醒，而牛奶过期天天报。两类物品语义不对称是缺陷不是设计。
  - 新增 `DueStatus.RENEWAL_OVERDUE`（标签「续费逾期」）而不是复用 `OVERDUE`：复用只需一行，但 `NotificationHelper` 的逾期文案是「已过期 N 天，检查还能不能用」，对订阅说这句话不成立——**文案是这次必须新增状态的真实理由**
  - 频率按"每天都报，直到处理"，与 EXPIRY 逾期同构；静音沿用既有的「今天不再提醒」/「稍后 3 天」，出口沿用「续期」（`rollForward` 的 `while (next < today)` 会把落下的周期一次追平）
  - 顺手把今日屏分组从两处 `filter` 改成 `DueStatus.todayGroup()`：`filter` 是"点名式"的，新状态没被点名就**静默不上屏**，正是这次 bug 的成因类别；改成 `when` 之后新增状态漏改任何一处（`labelZh`/`ringSpec`/`StatusTone`/通知文案/分组）都编译不过
  - 证据：单测 55 → **61 全绿**（新增 6 条：逾期出状态、-1/-7/-200 天都报、专属标签、满环显示逾期天数、handled 与 snooze 仍能静默、每个状态都有下落）；真机三条面全验：今日屏紧急组从 1 变 2 且视频会员满环「1」、详情浮层标签「续费逾期」、通知正文 `「视频会员」扣费日已过 1 天，没在用的话记得取消`（`dumpsys notification --noredact` 取的实文）
- 「每日提醒时间」接线完成（2026-09-25，#50；**先前遗留 ⑥ 的描述是错的**：设置页里根本没有这一行，`ReminderScheduler` 写死 `HOUR = 9`，用户既改不了也不知道几点会被打扰——不是"设置项没接线"，是压根没有设置项)
  - 纯逻辑抽成 `core/domain/ReminderTiming.kt` 两个函数并先写测试：`nextDailyFire`（压线算明天，用户刚改完不该立刻被弹）、`shouldNotifyNow`（兜底路径的闸）；prefs 存 hour/minute + `lastNotifiedDay`
  - **顺带修掉一个会让这个设置变成假话的问题**：WorkManager 每 24h 的兜底 `DailyScanWorker` 也会发通知，而它自己的调度时刻与用户设定的时刻无关——不拦就是"设了 20:30 结果凌晨 3 点弹"。现在 `NotificationHelper.postDailyReminders` 成为统一出口，`respectSchedule` 参数区分两条路：兜底过闸，精确闹钟本身与用户操作后的 runNow 不过闸（后者要当场刷新，`notifyAll` 顺带清扫陈旧子通知，跳过它反而让旧通知滞留）
  - 设置页新增「每日提醒」卡：显示当前时刻 + **下次提醒：9月26日 09:00**（只写 HH:mm 用户无法判断是今天还是明天）+ 未允许精确闹钟时明示"可能延到最长 24 小时兜底"。TimePicker 仍是 `@ExperimentalMaterial3Api`，所以卡片单独成 composable 把 opt-in 关在小作用域里，没有给整个设置页加注解
  - 证据：单测 61 → **71 全绿**（`ReminderTimingTest` 10 条：未到点/已过点/压线/一分钟前/月末年末进位/凌晨档/非法值钳位 + 兜底闸三条）；**真机端到端**：设成 11:30 → `dumpsys alarm` 显示 `origWhen=2026-09-25 11:30:00` → 等到 11:32 确认它真的响了（通知栏 `「酸奶」已过期 3 天…` + `「视频会员」扣费日已过 1 天…`）→ 闹钟自动续排到 `2026-09-26T11:30`（设备 GMT）→ 改回 09:00 后续排 `2026-09-26 09:00` ✓ 这条链从 UI 到 prefs 到 AlarmManager 到通知到自我续排，每一环都有观测证据
- 已知遗留（M3/M4 取消后，这就是剩余待办池）：① AddEdit 由 CONSUMABLE 改类时 quantity/unit/lowStockThreshold 残留（引擎按 reminderKind 分发，暂无行为影响）；② `ItemRepository.consumeOne` 无生产调用方（保留待「吃完」快捷操作或后续删除）；③ Snackbar replay=1 撤销按钮二次点击会再写一次 updatedAt（撤销恢复的快照用后即清，二次点击不会重复回滚）；④ `activeNotifications` 可加空防御。（⑤ 备份不携带墓碑、⑦ SyncMerge 平局语义 两项已由上面的恢复语义重做一并解决；**⑥「每日提醒时间」也已在上面解决**——它原先的描述本身就是错的，设置页里从来没有那一行，是 `ReminderScheduler` 写死 9 点。编号保留不复用）
- 人工验收清单（脚本无法覆盖的 UI 手测项，源自 Task 6/8/9/10/11/12 brief）：

| 项 | 来源 | 手测步骤 | 通过标准 |
|---|---|---|---|
| 备份/恢复＝时间点还原 | 2026-09-25 重做 | 导出 → 改一条（改名+改到期日）→ 导入该文件 → 看确认框 → 替换全部 | 确认框先报账（写回 N 条 / 移出 M 件）；替换后那条**回到导出时的样子**（你报的缺陷）；提示条点「撤销」整库换回 |
| 移出不是删库 | 同上 | 备份之后新加一件 → 导入旧备份 → 去清单看它消失 | 只在库里打墓碑、没被物理删除；再导一份**更新的**备份它就回来 |
| 空备份拒绝 | 同上 | 手工把文件 items 改成 `[]` 再导入 | 提示"这份备份里没有任何条目，已取消"，清单**一条不动** |
| 每日提醒时间 | 2026-09-25 #50 | 设置→每日提醒→改成两分钟后 → 等到点 → 再改成 20:30 看「下次提醒」是否变明天 | 到点真的弹出通知；改时刻后闹钟跟着改（`adb shell "dumpsys alarm \| grep expirykeeper"` 能核对）；未允许精确闹钟时卡片下方有黄字说明 |
| 订阅过期会报 | 2026-09-25 新增 | 把某订阅的下次扣费日改成昨天 → 看今日屏 | 它出现在**紧急**组、到期环满弧显示逾期天数、详情浮层标签是「续费逾期」；通知文案是扣费口径（不是"检查还能不能用"）；点「续期」后一次追平到未来并静默 |
| 今日屏快速操作 | T8 | 造 3 条（明天到期/已过期/低库存）→点「今天不再提醒」/「稍后3天」/「续期」 | 分组与 DueRing 数字正确；处理/延后即时消失且重进不现；续期后到期日=today+shelfLife；暗色全页可读 |
| 清单搜索排序分组 | T9 | 搜「牛奶」实时过滤/清空恢复；切 4 种排序；看分组计数 | 无到期日者沉底；分组计数与明细一致；~30 条无 jank |
| 添加三步 & emoji | T10 | 「牛奶，开封 3 天」零键盘路径；自定义 🐠 保存；空名/双空规则校验；编辑 M1 旧数据 | ≤15 秒完成；emoji 在今日/清单/详情/通知标题均显示；非法输入按钮禁用且提示明确；旧数据不丢字段不崩 |
| 详情/设置/撤销 | T11 | 长按→详情浮层；删除→Snackbar 撤销；权限卡两行；动态色开关；概览计数 | 详情数据正确；撤销后物品回归且到期日不变；权限状态真实反映；开关即时变色/回落 teal；概览与清点一致 |
| 通知聚合与延后 | T12 | 造 4 条到期→看通知栏分组摘要→点「稍后 3 天」 | 聚合成组、点开列表正确；延后后该通知消失且今日屏同步消失 |

### M2 品类完备
- [x] CONSUMABLE / RECURRING 语义（v2 T4 已交付）
- [x] 品类模板与保质期常识库（2026-09-24：9 品类共 45 条 `SubCategory` 常见物品模板，点一下预填名称/emoji/保质期或续费周期；**模板不落库**，物品不记得用过哪个模板，因此零 schema 变更、零迁移风险。`ExpiryForm.planFill` 三条规则由单测钉住：名字不覆盖手输、CONSUMABLE 不编造保质期、只有 EXPIRY 才切开封模式）
- [x] 多档提醒偏移、通知渠道分组（v2 T12 已交付）
- [ ] ~~条码扫描（MLKit）+ 可选在线查询~~ —— **2026-09-24 用户拍板：彻底不做**。MLKit 能解出码但解不出「这是什么商品」，中文条码→品名没有稳定免费 API（spec §8 已预见「失败则降级纯手动填名」），而「条码（选填）」字段现在就能手填；在拿到可用数据源之前，扫描只提供一串数字，价值不抵引入 CameraX+MLKit+相机权限的成本。M2 就此收口，直接进 M3
- 验收：6 品类各录 3 件真实物品，全家桶提醒正确 —— 常识库上线后录入路径已缩到「选品类 → 点常见 → 保存」两次点击

### M3 同步协议 —— **已取消（2026-09-25 用户决定：没有服务器，不做多人功能）**
- 设计规格 `docs/superpowers/specs/2026-09-24-m3-sync-design.md` 已写完并自审，随即**作废**（文件保留作决策记录，不实现）
- 事实备注（供将来回看）：规格选的拓扑 A 靠**网盘/NAS 客户端同步一个共享目录**，不需要自建服务器；但用户决定不做，此项连同 M4 一并撤下
- 因此 `SyncMerge` 的平局语义（遗留 ⑦）**不再是待修项**：唯一的调用方是手动备份恢复，`PREFER_INCOMING`（信手上这份）正是恢复该有的语义
- 不变：`change_log` 继续只写不读、`SyncMerge` 只服务恢复路径、备份导出不带墓碑（`getAll()` 过滤）——都是正确行为

### M4 家庭共享 —— **随 M3 取消**（依赖同步通道，同步不做则无从谈起）
- 原计划：WebDavDriver / 二维码配对 / 成员标识。全部撤下
- 单机版仍然成立的"全家受益"路径：一台手机管全家物品，或者各自装 App 各自用

### M5 打磨
- [ ] 物品照片、月度统计（丢弃成本）、桌面小组件/图标角标

## 决策与发现日志

- 2026-09-20 放弃 token 统计方向：开源过于成熟（codeburn/tokscale/aiusage 等，全 MIT）。调研成果保留在 `reference/`（4 个克隆仓库），与本项目无关，可删。
- 2026-09-22 立项"到期管家"：纯自用、家庭共享、本地优先零服务器、同步走 SyncDriver 接口（LocalFolder → WebDAV）、提醒全本地生成。
- 2026-09-25 **砍掉 M3 同步与 M4 家庭共享**（用户：没有服务器就不做多人功能）。项目定位从"家庭共享"收敛为**单机强工具**：本地优先这条从一开始就没破过，现在连"跨设备"这条也没了。规格 `2026-09-24-m3-sync-design.md` 作废留档。连带结论：`SyncMerge` 平局语义不用改、备份不带墓碑是可接受语义、为 M3 新建第二台 AVD 的需求消失。

## 风险雷达

| 风险 | 状态 |
|---|---|
| 录入摩擦导致弃用 | M1 先用起来验证，模板把添加压到 15 秒内 |
| MagicOS 杀后台导致通知不准时 | M1 真机验证；备选：充电时补扫 + 打开 App 时补发 |
| 中文条码库 API 不可用 | 已失效——条码功能 2026-09-24 彻底不做 |
| 没有服务器，多人共享无法落地 | 已失效——M3/M4 于 2026-09-25 取消，项目定位为单机工具 |
| 目录名仍叫 MyTokens | 待用户确认是否改目录/工程名 |
