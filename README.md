# 到期管家 · ExpiryKeeper

> 本机优先的家庭物品到期管家。食物、药品、化妆品、数码保修、证件、会员订阅，一处管理，到期前提醒。
>
> Local-first household expiry tracker for Android. No account, no server, no network.

<br>

| | | |
|:---:|:---:|:---:|
| <img src="docs/screenshots/01-today.png" width="220"> | <img src="docs/screenshots/02-list.png" width="220"> | <img src="docs/screenshots/03-add-template.png" width="220"> |
| **今日** · 分组与就地操作 | **清单** · 搜索与状态 | **添加** · 品类与常见模板 |
| <img src="docs/screenshots/04-detail-sheet.png" width="220"> | <img src="docs/screenshots/05-settings.png" width="220"> | <img src="docs/screenshots/06-today-dark.png" width="220"> |
| **详情浮层** · 页脚固定 | **设置** · 每日提醒与备份 | **暗色主题** · 同一套 tonal 层级 |

<br>

## 为什么做这个

冰箱深处的酸奶、药箱里开封半年的糖浆、忘了取消的会员——这类东西的共同点是**"没人记着它就一定浪费"**。日历提醒不适合（它是事件不是物品），备忘录取决于你愿不愿意手打一堆字段。

这个 App 的取舍是：**把"物品"当一等公民**，一个 `reminderKind` 收敛掉所有提醒语义，录入压到两次点击，然后每天在你要看的时间点，把真正需要处理的东西推到眼前。

纯自用工具，不上架、无商业化、无账号合规包袱。

## 功能

### 三种提醒语义

| 类型 | 语义 | 触发 |
|---|---|---|
| `EXPIRY` | 到期 / 过期 | 填到期日，或由 **开封日 + 保质期天数** 自动推导；逾期后每天提醒并显示逾期天数 |
| `CONSUMABLE` | 耗材余量 | 数量低于低库存线时提醒（洗衣液、纸巾、猫粮） |
| `RECURRING` | 周期扣费 | 续费前按提前量提醒；**扣费日一过转为「续费逾期」并每天提醒**，直到你续期或取消 |

### 录入：常识库把成本压到两次点击

9 个内置品类、45 条常见物品模板，把"该填几天"这个最烦的问题变成一次点击：

```
鲜牛奶 3 天 · 熟食·剩菜 3 天 · 叶菜 5 天 · 酸奶 14 天 · 鸡蛋 30 天 · 鲜肉 2 天
开封糖浆 / 眼药水 28 天 · 未开封药品 730 天
睫毛膏 / 眼线 180 天 · 面霜 12 个月
净水器滤芯 180 天 · 视频会员 30 天 · 驾驶证换证提前 90 天
```

模板**不落库**——物品不记得自己用过哪个模板，所以扩充常识库零 schema 变更、零迁移风险。

### 今日屏

- Hero 统计：待处理 / 两周内 / 全部
- 三个分组：**紧急**（逾期、今天到期、续费逾期）、**即将到期**（1–14 天窗口）、**需要关注**（低库存、今天扣费）
- 每张卡片下方直接给出 **续期 / 稍后 3 天 / 今天不再提醒**，不用进详情页就能处理完
- 到期环 DueRing：逾期显示逾期天数并走满环，今天显示「今」，其余按窗口递减

### 清单与详情

- 实时搜索（名称 / 备注 / 位置）、四种排序、按品类分组计数
- 详情浮层：状态胶囊、键值明细、最近 30 天事件流水，编辑/删除固定在底部不随内容漂

### 通知

- 每日提醒时刻可调（默认 09:00），设置页直接显示"下次提醒：9月26日 09:00"
- 多条到期聚合为分组摘要；通知上带 **稍后 3 天** 动作，点完今日屏同步消失
- 到点之前不会发通知——包括 WorkManager 的 24h 兜底扫描，避免"设了 20:30 却凌晨 3 点弹"

## 数据安全（这个项目最讲究的部分）

自用工具丢数据等于白用，所以这块的规则比功能多：

- **软删除，永不物理删除。** 删除只是给 `deletedAt` 打一个时间戳，参与后续所有合并判断。
- **备份 = 时间点还原。** 导入前先把账目摊开让你确认（写回几条、移出几件、移出的是哪些）；备份里没有的物品只打墓碑，不销毁；空备份直接拒绝。
- **导出携带墓碑**，所以"删掉的东西"这个事实也进得了备份文件。
- **严格解析。** 备份文件里任何一行结构不对 → 整个文件丢弃，绝不"尽力解析能读多少算多少"。
- **迁移有测试。** Room 1→2 迁移用 `MigrationTestHelper` 在真机跑过，断言行数与内容不变；没有开 `fallbackToDestructiveMigration`，schema 不匹配会失败而不是悄悄清库。
- **删除可撤销。** 详情里删除后 Snackbar 提供一次性撤销，撤销闭包用后即弃，重复点击不会二次写入。
- **没有任何凭证、服务器地址或网络调用。** 数据只在本机 Room 与你自己选的 SAF 文件里。

## 技术栈

```
Kotlin 2.3.21（由 AGP 9 内置工具链编译，无独立 kotlin-android 插件）
Jetpack Compose + Material 3 1.4（Compose BOM 2026.09.00）
Room 2.8.5 + KSP · WorkManager 2.11.2
compileSdk 37 / targetSdk 37 / minSdk 29
单模块 :app，48 个 .kt 文件
```

分层是硬约束，不是建议：

```
core/data         Room 实体、DAO、Repository、偏好
core/domain       纯函数：提醒引擎、表单状态、备份格式、恢复计划、日期文案
core/ui           设计系统（卡片、胶囊、区块头、快速操作、排版、配色）
feature/*         今日 / 清单 / 添加编辑 / 详情浮层 / 设置
notifications     渠道、聚合、精确闹钟 + WorkManager 兜底
```

`core/domain` 不依赖 Android，所以提醒规则、日期格式、备份解析、恢复计划全部在 JVM 上测。

## 构建与运行

需要 Android Studio（或 JBR 21+）与 Android SDK 37。命令行：

```bash
export JAVA_HOME=/path/to/jbr        # Windows 上例：D:/Apps/AndroidStudio/jbr
./gradlew.bat assembleDebug          # 或 ./gradlew assembleDebug
./gradlew.bat installDebug
```

跑门禁：

```bash
./gradlew.bat testDebugUnitTest lintDebug        # 75 条 JVM 单测 + lint 0 error
./gradlew.bat connectedDebugAndroidTest          # Room 迁移 / 恢复落库 / 种子数据
bash scripts/smoke-routes.sh                     # 全路由冒烟：逐屏点一遍，专抓"一进去就崩"
```

想在模拟器里看点真实内容而不是空列表，用内置的种子数据（7 条，覆盖逾期 / 临期 / 今天扣费 / 低库存 / 窗口外）：

```bash
./gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell "am instrument -w -e class 'com.expirykeeper.SeedDataTest#seed' -e seed on \
  com.expirykeeper.test/androidx.test.runner.AndroidJUnitRunner"
```

> 注意：`connectedDebugAndroidTest` 跑完会回滚安装并删掉数据目录，所以种完数据要用上面这条 `am instrument`，别用 gradle 的 connected 任务。

## 工程约定

- [`docs/CODESTYLE.md`](docs/CODESTYLE.md) — 分层、命名、注释与测试约定
- [`docs/PROGRESS.md`](docs/PROGRESS.md) — 活文档：里程碑、每次修复的根因与证据、已知遗留（目前**清零**）
- [`docs/UI-AUDIT.md`](docs/UI-AUDIT.md) — UI 审计：取证方法、逐条问题与整改批次、被否决的方案及原因
- [`docs/superpowers/specs/`](docs/superpowers/specs/) — 设计规格
- [`docs/superpowers/plans/`](docs/superpowers/plans/) — 实现计划

这个项目里所有 bug 修复都遵循同一套流程：先在真机复现并量化（`uiautomator` 取 bounds、截图取像素算对比度），再定位根因，写一条会失败的测试，最后才改代码。文档里因此也留下了若干"我自己写错又改回来"的记录——它们比成功记录更有防重复价值。

## 明确的边界（不做什么，以及为什么）

| 不做 | 原因 |
|---|---|
| 多设备同步 / 家庭共享 | 需要服务器或第三方网盘，而"没有服务器"是这个项目的前提。数据在本地，换机靠导出/导入 |
| 条码扫描 | MLKit 能解出码但解不出"这是什么商品"，中文条码→品名没有稳定的免费数据源。条码字段保留为手填 |
| 桌面小组件 / 图标角标 | 评估后取消，收益不抵新增的 widget 提供方与更新时机 |
| 端到端加密 | 无云存储，因此无传输面；本地数据由系统沙箱保护 |
| 字段级冲突合并 | 没有并发写入面（单设备），因此不需要 |

## 许可证

个人自用项目，暂未选择开源许可证。如需使用请先联系仓库所有者。
