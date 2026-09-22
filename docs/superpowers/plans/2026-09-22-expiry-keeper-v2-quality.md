# 到期管家 v2 品质升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 M1 的"能跑"升级为符合用户四条红线的品质版：零致命 bug / 数据可备份 / Google·Apple 级 Material 3 视觉 / emoji 丰富的内容 / feature-core 清晰分层。

**Architecture:** 单模块 `app`，包内分层：`core/data`（Room + Repository）、`core/domain`（纯函数：提醒引擎、LWW 合并、备份 JSON）、`core/ui/designsystem`（主题 + 复用组件）、`feature/*`（today / list / addedit / detail / settings 各屏）、`notifications/`（闹钟 + Worker）。不引入 Hilt（手工 AppContainer 注入，个人项目 YAGNI）。备份导入与未来 WebDAV 同步共用同一个 LWW 合并纯函数。

**Tech Stack:** Kotlin（AGP 9.3.3 内置，无 kotlin.android 插件）· Jetpack Compose + Material 3（BOM 2026.09.00，动态取色）· Room 2.8.5（KSP）· WorkManager 2.11.2 · Navigation Compose 2.10.1 · org.json（Android 内置，备份序列化，不引 kotlinx.serialization 以避开插件版本风险）。

**Spec:** `docs/superpowers/specs/2026-09-22-expiry-keeper-design.md`（§9 为本次四条硬性要求，逐条对应下文任务）

## Global Constraints

每条都是红线，违反即任务不通过（抄自 spec §9 与环境备忘）：

1. **稳健**：零崩溃、零丢数据；所有写路径要么有单测、要么有迁移测试兜底；备份/导入是 M 级验收项。
2. **美观**：Material 3；Android 12+ 开动态取色；大圆角卡片；display 级大标题数字；暗色模式完整适配；动效克制（仅 AnimatedVisibility / 按压缩放）。
3. **内容丰富**：9 品类 emoji 预设 + 物品级自定义 emoji；搜索、排序、事件流水（近 30 天处理记录）、统计概览、快速操作（续期/稍后/今天不再提醒）。
4. **工程规范**：包结构 `core/{data,domain,ui}` + `feature/*` + `notifications/`；一文件一职责；`docs/CODESTYLE.md` 为风格基准；`reference/` 永不进 git。
5. AGP 锁 **9.3.3**（Studio 上限），禁止添加 `org.jetbrains.kotlin.android` 插件，禁止 `kotlinOptions`/顶层 `kotlin{}`；compose 插件 `org.jetbrains.kotlin.plugin.compose` 保留。
6. minSdk 29 / compileSdk 37；不新增第三方依赖，除非本计划某任务明确列出（白名单：`androidx.room:room-testing`、`androidx.test.ext:junit`、`org.json:json:20240303`(仅 testImplementation)）。
7. 构建/测试统一命令模式（工作目录 = 仓库根，Git Bash）：
   `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat <tasks> > /tmp/build.log 2>&1; echo "EXIT=$?" >> /tmp/build.log; tail -5 /tmp/build.log`
   **禁止把 gradlew 输出直接管道给 tail**（掩埋退出码）。
8. 每个任务结尾必须：单元测试全绿 + `:app:assembleDebug` 成功 + git commit。

## File Structure（v2 终态）

```
app/src/main/java/com/expirykeeper/
├─ App.kt                          # Application + AppContainer（唯一手工 DI 点）
├─ MainActivity.kt                 # 权限请求 + EkTheme{EkApp}
├─ EkApp.kt                        # NavHost + 底部导航（从 ui/ 提到根包）
├─ core/
│  ├─ data/
│  │  ├─ Item.kt                   # 实体 + ReminderKind + ItemEvent 实体
│  │  ├─ Converters.kt             # List<Int> <-> CSV
│  │  ├─ ItemDao.kt                # observe/getById/upsert/softDelete/setQuantity/roll/…
│  │  ├─ ChangeLogEntry.kt  ChangeLogDao.kt
│  │  ├─ AppDatabase.kt            # version 2, exportSchema=true → schemas/
│  │  ├─ Categories.kt             # 9 品类 emoji 预设
│  │  └─ ItemRepository.kt         # 写路径统一：updatedAt + change_log + 派生 expireAt
│  ├─ domain/
│  │  ├─ ReminderEngine.kt         # 纯函数：snooze/handled 过滤、rollForward、upcoming、summary
│  │  ├─ SyncMerge.kt              # 纯函数 LWW 合并（备份导入 & M3 同步共用）
│  │  └─ Backup.kt                 # 纯函数 JSON ⇄ List<Item>（org.json）
│  └─ ui/designsystem/
│     ├─ Theme.kt Color.kt Type.kt Shapes.kt   # 动态取色 + M3 Expressive 档位
│     └─ Components.kt             # StatusPill SectionHeader EmptyState ItemCard BigHeader FlowChips
├─ notifications/
│  ├─ NotificationHelper.kt  ReminderScheduler.kt
│  ├─ ReminderAlarmReceiver.kt  DailyScanWorker.kt  BootReceiver.kt
├─ feature/
│  ├─ today/TodayScreen.kt
│  ├─ list/ListScreen.kt
│  ├─ addedit/AddEditScreen.kt     # + EmojiPicker.kt
│  ├─ detail/DetailSheet.kt        # 详情 + 事件流水 ModalBottomSheet
│  └─ settings/SettingsScreen.kt   # 权限卡 + 动态色开关 + 备份/恢复 + 数据概览
└─ ui/ItemsViewModel.kt            # 暂留根 ui 包：唯一 VM，feature 共享（避免过度拆分）
app/src/test/java/com/expirykeeper/  # 全部 JVM 纯函数测试
app/src/androidTest/java/com/expirykeeper/  # Room 迁移测试
app/schemas/                       # Room 导出的 JSON schema（进 git）
docs/CODESTYLE.md
```

**Why this decomposition:** designsystem 独立成包 → 所有 feature 屏只依赖组件不复彼此的UI；domain 三件套全是纯 JVM 函数 → 四条红线中"稳健"由单测直接覆盖；备份与同步共用 SyncMerge → M3 到来不必重写。

---

### Task 1: git 纳管 + 代码风格基线

**Files:**
- Create: `.gitignore`（若已存在则核对内容）、`docs/CODESTYLE.md`、`.editorconfig`
- Delete-after-check: 确认 `local.properties`、`build/`、`.gradle/`、`reference/`、`*.log` 不入库

**Interfaces:**
- Produces: 后续所有任务以 `git add <files> && git commit -m "..."` 收尾；`docs/CODESTYLE.md` 是评审引用基准。

- [ ] **Step 1: 写 .gitignore**

```gitignore
*.iml
.gradle/
build/
local.properties
.idea/
.kotlin/
reference/
*.log
.cxx/
```

- [ ] **Step 2: 写 .editorconfig**

```ini
root = true

[*]
charset = utf-8
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 3: 写 docs/CODESTYLE.md（全文）**

```markdown
# 到期管家 代码风格

- 包分层：core.data（持久化）/ core.domain（纯函数，零 Android import）/ core.ui.designsystem（可复用 UI）/ feature.<屏>（仅本屏用）/ notifications（系统调度）。feature 之间不得互相 import；domain 不得 import androidx.*（org.json 例外仅限 Backup，见 §依赖）。
- 一文件一职责；>300 行的 Compose 屏文件按 @Composable 私有子组件拆文件。
- 命名：屏幕 = XxxScreen；组件 = 名词（StatusPill）；纯函数对象 = 名词单数（ReminderEngine）；ViewModel = XxxViewModel。
- Compose：State 上游提升；Modifier 是第一可选参数；@Composable 不做 IO，IO 走 suspend Repository。
- 数据变更唯一入口 = ItemRepository（保证 change_log 不漏记）。
- 日期一律 java.time（LocalDate/epochDay），存储用 Long epochDay，不存字符串。
- 注释：只写"为什么"，中文可；不写"做了什么"。
- 测试：domain 纯函数 100% JVM 单测；Room 查询用 androidTest；UI 走验收清单手测。
- 提交信息：conventional commits（feat/fix/test/docs/refactor/chore），中英皆可，一提交一任务产出。
```

- [ ] **Step 4: git init + 基线提交**

```bash
git init
git add .gitignore .editorconfig docs app gradle build.gradle.kts settings.gradle.kts gradle.properties gradlew.bat gradlew
git status   # 人工核对：reference/、local.properties、build/ 均不在列
git commit -m "chore: baseline M1 snapshot with codestyle and editorconfig"
```
若 `git add` 意外带入 reference/ 或 build/，立即 `git rm -r --cached <path>` 修正 .gitignore 后重新提交。

- [ ] **Step 5: 验证构建仍绿 + 提交**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest > /tmp/build1.log 2>&1; echo "EXIT=$?" >> /tmp/build1.log; tail -5 /tmp/build1.log`
Expected: `EXIT=0`，9 个旧测试绿。若 Step 4 后尚有未跟踪残留（如 docs/superpowers/），追加 `git add -A && git commit -m "docs: import specs plans and progress tracker"`。

---

### Task 2: 包重排 core / feature / notifications（纯搬移）

**Files（git mv 映射，包声明与 import 同步改）:**
- Move: `data/*` → `core/data/`；`reminders/ReminderEngine.kt` → `core/domain/`；`reminders/{NotificationHelper,ReminderScheduler,ReminderAlarmReceiver,DailyScanWorker,BootReceiver}.kt` → `notifications/`；`ui/Theme.kt` → `core/ui/designsystem/Theme.kt`；`ui/{TodayScreen,ListScreen,AddEditScreen}.kt` → `feature/{today,list,addedit}/`；`ui/EkApp.kt` → 根包；`ui/ItemsViewModel.kt` 留 `ui/`
- Modify: 全部 import 点（MainActivity、App、测试文件）

**Interfaces:**
- Consumes: 现有一切公开签名不变。
- Produces: 终态包路径 —— 后续任务的文件地址以此为准。

- [ ] **Step 1: 执行搬移**

```bash
mkdir -p app/src/main/java/com/expirykeeper/core/data app/src/main/java/com/expirykeeper/core/domain app/src/main/java/com/expirykeeper/core/ui/designsystem app/src/main/java/com/expirykeeper/notifications app/src/main/java/com/expirykeeper/feature/today app/src/main/java/com/expirykeeper/feature/list app/src/main/java/com/expirykeeper/feature/addedit
git mv app/src/main/java/com/expirykeeper/data/*.kt app/src/main/java/com/expirykeeper/core/data/
git mv app/src/main/java/com/expirykeeper/reminders/ReminderEngine.kt app/src/main/java/com/expirykeeper/core/domain/
git mv app/src/main/java/com/expirykeeper/reminders/NotificationHelper.kt app/src/main/java/com/expirykeeper/reminders/ReminderScheduler.kt app/src/main/java/com/expirykeeper/reminders/ReminderAlarmReceiver.kt app/src/main/java/com/expirykeeper/reminders/DailyScanWorker.kt app/src/main/java/com/expirykeeper/reminders/BootReceiver.kt app/src/main/java/com/expirykeeper/notifications/
git mv app/src/main/java/com/expirykeeper/ui/theme/Theme.kt app/src/main/java/com/expirykeeper/core/ui/designsystem/
git mv app/src/main/java/com/expirykeeper/ui/TodayScreen.kt app/src/main/java/com/expirykeeper/feature/today/
git mv app/src/main/java/com/expirykeeper/ui/ListScreen.kt app/src/main/java/com/expirykeeper/feature/list/
git mv app/src/main/java/com/expirykeeper/ui/AddEditScreen.kt app/src/main/java/com/expirykeeper/feature/addedit/
git mv app/src/main/java/com/expirykeeper/ui/EkApp.kt app/src/main/java/com/expirykeeper/
rmdir app/src/main/java/com/expirykeeper/reminders app/src/main/java/com/expirykeeper/ui/theme
mkdir -p app/src/test/java/com/expirykeeper/domain
git mv app/src/test/java/com/expirykeeper/reminders/ReminderEngineTest.kt app/src/test/java/com/expirykeeper/domain/
rmdir app/src/test/java/com/expirykeeper/reminders
```

- [ ] **Step 2: 批量改包声明与 import**

对每个移动过的文件改首行 `package`（示例：`package com.expirykeeper.core.data`）。全局替换 import：
- `com.expirykeeper.data.` → `com.expirykeeper.core.data.`
- `com.expirykeeper.reminders.ReminderEngine` / `DueStatus` / `Reminder` 所在 import → `com.expirykeeper.core.domain.`
- `com.expirykeeper.reminders.{NotificationHelper,ReminderScheduler,...}` → `com.expirykeeper.notifications.`
- `com.expirykeeper.ui.theme.EkTheme` → `com.expirykeeper.core.ui.designsystem.EkTheme`
- 屏文件 import（TodayScreen/ListScreen/AddEditScreen 现属 `com.expirykeeper.feature.*`，EkApp.kt 需新 import 它们）

用 Edit 工具逐文件替换，不用 sed（避免误伤）。ReminderEngineTest.kt 顶部 import 同步改。

- [ ] **Step 3: 跑测试验证纯搬移**

Run: 同 Task 1 Step 5 命令
Expected: `EXIT=0`，9 测试仍全绿。搬移任务改动面大，任何编译错都要在本次修完，不留给下个任务。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: repackage into core/feature/notifications layering (pure move)"
```

---

### Task 3: Room v2 —— 新字段 + 事件流水表 + exportSchema + 迁移测试

**Files:**
- Modify: `core/data/Item.kt`（新列 + ItemEvent 实体）、`core/data/AppDatabase.kt`（version 2 + MIGRATION_1_2 + events 表）、`core/data/ItemDao.kt`（新方法）、`core/data/ItemRepository.kt`（派生 expireAt + 事件记录）
- Create: `core/data/Converters.kt` 无需改；`app/src/androidTest/java/com/expirykeeper/RoomMigrationTest.kt`
- Modify: `app/build.gradle.kts`（room-testing + ksp schemaLocation + testInstrumentationRunner）、`gradle/libs.versions.toml`（room-testing、androidx-test-ext-junit）、`settings.gradle.kts` 不动
- Generate: `app/schemas/com.expirykeeper.core.data.AppDatabase/1.json、2.json`

**Interfaces:**
- Produces: `Item` 新字段（后续所有任务共用）：
  `emoji: String?`（物品级 emoji，null=用品类 emoji）、`openedAtEpochDay: Long?`、`shelfLifeDays: Int?`、`handledAtEpochDay: Long?`、`handledStatus: String?`、`snoozedUntilEpochDay: Long?`
- Produces: `data class ItemEvent(@PrimaryKey(autoGenerate=true) id: Long?, val itemId: String, val kind: String, val epochDay: Long, val createdAt: Long)`；kind ∈ `"add","edit","roll","snooze","handle","delete"`
- Produces: `ItemDao.observeById(id: String): Flow<Item?>`、`suspend fun setExpire(id: String, epochDay: Long, now: Long)`、`EventDao.insert(e: ItemEvent)`、`EventDao.since(epochDay: Long): List<ItemEvent>`
- Produces: `ItemRepository.save(item)` 现在会：派生 `expireAtEpochDay`（若为空且 shelfLifeDays 非空 → `(openedAtEpochDay ?: createdAt 的 epochDay) + shelfLifeDays`）并记 add/edit 事件；新增 `suspend fun rollForward(id: String, today: LocalDate): Boolean`、`markHandled(id: String, status: String, today: LocalDate)`、`snooze(id: String, days: Int, today: LocalDate)`

- [ ] **Step 1: 加依赖（libs.versions.toml + app/build.gradle.kts）**

`gradle/libs.versions.toml`：

```toml
[libraries]
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version = "1.3.0" }
```

`app/build.gradle.kts`（defaultConfig 内 + 文件尾部）：

```kotlin
defaultConfig {
    // ...existing...
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Room schema 导出目录
}
// 与 android { } 平级：
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
dependencies {
    // ...existing...
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```
若 `ksp{}` 块在 AGP9 报错，回退方案：android 块内 `sourceSets` 不需要，改用 `ksp("room.schemaLocation", ...)` 同义写法 `add("ksp", ...)` 不成立时记录到 PROGRESS 并改用 `androidx.room` 官方 Gradle 插件 `alias(libs.plugins.room)`（plugin id `androidx.room`，version.ref room）+ `room { schemaDirectory("$projectDir/schemas") }`。

- [ ] **Step 2: Item.kt 加字段（在 deletedAt 之前插入）**

```kotlin
    /** 物品级 emoji 图标；null 则回退品类预设 */
    val emoji: String? = null,
    /** 开瓶/开封日期 epochDay；与 shelfLifeDays 联合推导到期日 */
    val openedAtEpochDay: Long? = null,
    val shelfLifeDays: Int? = null,
    /** 今日已处理（不再提醒）的日期与状态名，次日自动失效 */
    val handledAtEpochDay: Long? = null,
    val handledStatus: String? = null,
    /** 延后提醒截止日：today <= 该值时引擎静默 */
    val snoozedUntilEpochDay: Long? = null,
```

同文件追加：

```kotlin
@Entity(tableName = "events")
data class ItemEvent(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    val itemId: String,
    val kind: String,
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 3: DAO 新查询**

`ItemDao.kt` 追加：

```kotlin
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: String): Flow<Item?>

    @Query("UPDATE items SET expireAtEpochDay = :epochDay, updatedAt = :now WHERE id = :id")
    suspend fun setExpire(id: String, epochDay: Long, now: Long)

    @Query("UPDATE items SET handledAtEpochDay = :day, handledStatus = :status, updatedAt = :now WHERE id = :id")
    suspend fun setHandled(id: String, status: String, day: Long, now: Long)

    @Query("UPDATE items SET snoozedUntilEpochDay = :until, handledAtEpochDay = NULL, handledStatus = NULL, updatedAt = :now WHERE id = :id")
    suspend fun setSnoozedUntil(id: String, until: Long, now: Long)
```

Create `EventDao.kt`：

```kotlin
package com.expirykeeper.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventDao {
    @Insert suspend fun insert(e: ItemEvent)
    @Query("SELECT * FROM events WHERE epochDay >= :fromEpochDay ORDER BY createdAt DESC")
    suspend fun since(fromEpochDay: Long): List<ItemEvent>
    @Query("SELECT COUNT(*) FROM events WHERE kind = 'roll' AND epochDay >= :fromEpochDay")
    suspend fun rollCountSince(fromEpochDay: Long): Int
}
```

- [ ] **Step 4: AppDatabase v2 + 手写迁移**

```kotlin
@Database(
    entities = [Item::class, ChangeLogEntry::class, ItemEvent::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun changeLogDao(): ChangeLogDao
    abstract fun eventDao(): EventDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration() {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN emoji TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN openedAtEpochDay INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN shelfLifeDays INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN handledAtEpochDay INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN handledStatus TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN snoozedUntilEpochDay INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }
    }
}
```

`App.kt` 的 `databaseBuilder(...)` 链上加 `.addMigrations(AppDatabase.MIGRATION_1_2)`（不加 `fallbackToDestructiveMigration` —— 红线：不许丢数据）。

- [ ] **Step 5: Repository 扩展**

`ItemRepository.kt` 构造参数追加 `private val eventDao: EventDao`（App.kt 同步传入）。

```kotlin
    suspend fun save(item: Item) {
        val now = System.currentTimeMillis()
        val todayEpoch = java.time.LocalDate.now().toEpochDay()
        val derived = if (item.expireAtEpochDay == null && item.shelfLifeDays != null && item.reminderKind == ReminderKind.EXPIRY) {
            val base = item.openedAtEpochDay ?: (item.createdAt / 86_400_000L)
            item.copy(expireAtEpochDay = base + item.shelfLifeDays)
        } else item
        val isNew = itemDao.getById(item.id) == null
        val isRoll = !isNew && derived.expireAtEpochDay != null &&
            itemDao.getById(item.id)?.expireAtEpochDay == derived.expireAtEpochDay
        val toWrite = derived.copy(updatedAt = now, lastModifiedBy = deviceId)
        itemDao.upsert(toWrite)
        changeLogDao.insert(ChangeLogEntry(itemId = item.id, op = "upsert", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = item.id, kind = if (isNew) "add" else if (isRoll) "edit" else "edit", epochDay = todayEpoch))
    }

    suspend fun softDelete(id: String) { /* 现有逻辑后 + eventDao.insert(ItemEvent(id, "delete", today)) */ }

    /** 快速操作"续期/吃完"：EXPIRY 按保质期滚动，RECURRING 按周期滚到未来；无法推导返回 false */
    suspend fun rollForward(id: String, today: java.time.LocalDate): Boolean {
        val item = itemDao.getById(id) ?: return false
        val target = when (item.reminderKind) {
            ReminderKind.EXPIRY -> item.shelfLifeDays?.let { today.toEpochDay() + it } ?: return false
            ReminderKind.RECURRING -> {
                var next = (item.nextDueAtEpochDay ?: today.toEpochDay()) + (item.recurrenceDays ?: return false)
                while (next < today.toEpochDay()) next += item.recurrenceDays!!
                next
            }
            ReminderKind.CONSUMABLE -> return false
        }
        itemDao.setExpire(id, target, System.currentTimeMillis())
        if (item.reminderKind == ReminderKind.RECURRING) {
            itemDao.upsert(item.copy(nextDueAtEpochDay = target, updatedAt = System.currentTimeMillis(), lastModifiedBy = deviceId))
        }
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = System.currentTimeMillis(), deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = id, kind = "roll", epochDay = today.toEpochDay()))
        return true
    }

    suspend fun markHandled(id: String, status: String, today: java.time.LocalDate) {
        itemDao.setHandled(id, status, today.toEpochDay(), System.currentTimeMillis())
        eventDao.insert(ItemEvent(itemId = id, kind = "handle", epochDay = today.toEpochDay()))
    }

    suspend fun snooze(id: String, days: Int, today: java.time.LocalDate) {
        itemDao.setSnoozedUntil(id, today.toEpochDay() + days, System.currentTimeMillis())
        eventDao.insert(ItemEvent(itemId = id, kind = "snooze", epochDay = today.toEpochDay()))
    }

    suspend fun recentEvents(days: Int = 30): List<ItemEvent> =
        eventDao.since(java.time.LocalDate.now().minusDays(days.toLong()).toEpochDay())

    suspend fun rollCount30d(): Int =
        eventDao.rollCountSince(java.time.LocalDate.now().minusDays(30).toEpochDay())
```
（save 中重复 getById 调用合并为一次局部变量 `val existing = itemDao.getById(item.id)`，上面代码为示意意图，落地时写一次查询版本；`isRoll` 变量无意义可删，kind 就是 "add"/"edit"。）

- [ ] **Step 6: 迁移 androidTest**

Create `app/src/androidTest/java/com/expirykeeper/RoomMigrationTest.kt`：

```kotlin
package com.expirykeeper

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expirykeeper.core.data.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsData() {
        helper.createDatabase("m1", 1).apply {
            execSQL("""INSERT INTO items (id,name,categoryId,reminderKind,expireAtEpochDay,reminderOffsetsDays,createdAt,updatedAt)
                VALUES ('i1','牛奶','food-chilled','EXPIRY',20000,'2,0',1,1)""")
            close()
        }
        helper.runMigrationsAndValidate("m1", 2, true, AppDatabase.MIGRATION_1_2).apply {
            query("SELECT name, emoji, snoozedUntilEpochDay FROM items").use { c ->
                assert(c.moveToFirst())
                assert(c.getString(0) == "牛奶")
                assert(isNull(1))
                assert(isNull(2))
            }
            query("SELECT COUNT(*) FROM events").use { c ->
                c.moveToFirst(); assert(c.getInt(0) == 0)
            }
            close()
        }
    }
}
```

注意：`helper.createDatabase(...)` 在 room 2.8 新 API 返回 `RoomTestDatabase`/`SupportSQLiteDatabase` 视版本而定 —— 编译期以 IDE 提示的 2.8.5 实际签名为准修正（MigrationTestHelper 构造也同理：2.8 推荐传 `AppDatabase::class.java`，若构造签名要求 config 列表则传 `emptyList()`）。Expected 迁移列与 v2 schema 完全一致。

- [ ] **Step 7: 构建 + 单测 + schema 生成验证**

Run: Task 1 Step 5 命令
Expected: `EXIT=0`；`app/schemas/.../1.json` 与 `2.json` 存在（首次跑 KSP 会生成 v2，v1 由 createDatabase 测试依赖 exportSchema 历史——若 v1.json 未生成属正常，从 git 基线不可得时删除本步对该文件的检查，MigrationTestHelper 依赖测试资产目录 `app/schemas` 以 `sourceSets.androidTest.assets.srcDirs += file("$projectDir/schemas")` 配置，加到 android{} 的 sourceSets）。
如缺 assets 配置，在 `android { }` 中补：

```kotlin
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(data): room v2 — emoji/opened-shelf-life/handled/snooze fields, events table, migration + exportSchema"
```

---

### Task 4: ReminderEngine v2 —— snooze/handled 静默 + 到期日显示辅助（TDD）

**Files:**
- Modify: `core/domain/ReminderEngine.kt`
- Modify(test): `app/src/test/java/com/expirykeeper/domain/ReminderEngineTest.kt`
- Modify: `notifications/NotificationHelper.kt`（文案使用 emoji，见 Step 5 —— 仅改 title 组装一行）

**Interfaces:**
- Consumes: Task 3 的 Item 新字段。
- Produces: `ReminderEngine.computeOne/computeForDate` 现自动跳过 snoozed 与当日 handled；新纯函数 `fun effectiveExpireDay(item: Item): Long?`（= expireAtEpochDay ?: opened/createdAt+shelfLife 派生）与 `fun displayIcon(item: Item, categoryEmoji: String): String`（emoji ?: 品类图标）—— UI 与通知共用。

- [ ] **Step 1: 写失败测试（追加到 ReminderEngineTest）**

```kotlin
    @Test fun snoozedItemStaysSilent() {
        val today = LocalDate.of(2026, 9, 22)
        val item = expiryItem(expire = today.toEpochDay()) // 既有的构造 helper
        val snoozed = item.copy(snoozedUntilEpochDay = today.toEpochDay() + 5)
        assertTrue(ReminderEngine.computeForDate(listOf(snoozed), today).isEmpty())
    }

    @Test fun handledTodaySilentButReturnsTomorrow() {
        val today = LocalDate.of(2026, 9, 22)
        val item = expiryItem(expire = today.toEpochDay()).copy(handledAtEpochDay = today.toEpochDay(), handledStatus = "DUE_TODAY")
        assertTrue(ReminderEngine.computeForDate(listOf(item), today).isEmpty())
        val tomorrow = today.plusDays(1)
        val r = ReminderEngine.computeForDate(listOf(item), tomorrow)
        assertEquals(1, r.size)
        assertEquals(DueStatus.OVERDUE, r[0].status)
    }

    @Test fun effectiveExpireDerivedFromOpenedShelfLife() {
        val opened = LocalDate.of(2026, 9, 1).toEpochDay()
        val item = expiryItem(expire = null).copy(openedAtEpochDay = opened, shelfLifeDays = 3)
        assertEquals(opened + 3, ReminderEngine.effectiveExpireDay(item))
    }
```
（若测试文件里没有 `expiryItem` helper，先在测试类顶部建：`private fun expiryItem(expire: Long?) = Item(id = "x", name = "n", categoryId = "food-chilled", expireAtEpochDay = expire)`。）

- [ ] **Step 2: 跑测试确认新用例红**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "*ReminderEngineTest*" > /tmp/t4.log 2>&1; echo "EXIT=$?" >> /tmp/t4.log; tail -20 /tmp/t4.log`
Expected: FAIL（unresolved reference: effectiveExpireDay 等）

- [ ] **Step 3: 实现**

`computeOne` 顶部加闸：

```kotlin
    fun computeOne(item: Item, today: LocalDate): Reminder? {
        item.snoozedUntilEpochDay?.let { if (today.toEpochDay() <= it) return null }
        val reminder = when (item.reminderKind) { /* 原三分支不变 */ }
        if (reminder != null && item.handledAtEpochDay == today.toEpochDay() &&
            item.handledStatus == reminder.status.name) return null
        return reminder
    }
```

新纯函数：

```kotlin
    fun effectiveExpireDay(item: Item): Long? = item.expireAtEpochDay
        ?: item.shelfLifeDays?.let { (item.openedAtEpochDay ?: (item.createdAt / 86_400_000L)) + it }

    fun displayIcon(item: Item, categoryEmoji: String): String = item.emoji ?: categoryEmoji
```

`expiry()` 分支改用 `effectiveExpireDay(item)` 取 expire。`AppDatabase`/repo 的 upsert 派生逻辑（Task 3）保证存储与派生一致，两处并存无害。

- [ ] **Step 4: 全绿**

Run: 同 Step 2。Expected: `EXIT=0`，12/12 绿（旧 9 + 新 3）。

- [ ] **Step 5: 通知标题带 emoji**

`NotificationHelper.kt` 组装通知处 `setContentTitle(item.name)` → `setContentTitle("${ReminderEngine.displayIcon(item, Categories.byId(item.categoryId)?.emoji ?: "📦")} ${item.name}")`。

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(domain): engine honors snooze/handled, effectiveExpireDay + displayIcon helpers"
```

---

### Task 5: SyncMerge —— LWW 合并纯函数（备份恢复与 M3 同步共用，TDD）

**Files:**
- Create: `core/domain/SyncMerge.kt`、`app/src/test/java/com/expirykeeper/domain/SyncMergeTest.kt`

**Interfaces:**
- Produces: `data class MergeResult(val toWrite: List<Item>, val skipped: Int)`；`object SyncMerge { fun merge(local: List<Item>, incoming: List<Item>): MergeResult }` —— Task 6 Backup 导入调用它。

- [ ] **Step 1: 失败测试**

```kotlin
package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergeTest {
    private fun item(id: String, updatedAt: Long, deletedAt: Long? = null, name: String = "n") =
        Item(id = id, name = name, categoryId = "c", updatedAt = updatedAt, deletedAt = deletedAt)

    @Test fun newerIncomingWins() {
        val r = SyncMerge.merge(listOf(item("a", 10, name = "old")), listOf(item("a", 20, name = "new")))
        assertEquals(listOf("new"), r.toWrite.map { it.name })
    }

    @Test fun newerLocalDefends() {
        val r = SyncMerge.merge(listOf(item("a", 30, name = "mine")), listOf(item("a", 20, name = "theirs")))
        assertEquals(0, r.toWrite.size)
        assertEquals(1, r.skipped)
    }

    @Test fun tiePrefersIncoming() {
        val r = SyncMerge.merge(listOf(item("a", 20, name = "mine")), listOf(item("a", 20, name = "incoming")))
        assertEquals(listOf("incoming"), r.toWrite.map { it.name })
    }

    @Test fun tombstoneReapesOldEdit() {
        val r = SyncMerge.merge(listOf(item("a", 10)), listOf(item("a", 20, deletedAt = 20)))
        assertEquals(1, r.toWrite.size)
        assertEquals(20L, r.toWrite[0].deletedAt)
    }

    @Test fun liveEditBeatsOldTombstone() {
        val r = SyncMerge.merge(listOf(item("a", 50, name = "revive")), listOf(item("a", 20, deletedAt = 20)))
        assertEquals(0, r.toWrite.size)
    }

    @Test fun freshIdsPassThrough() {
        val r = SyncMerge.merge(listOf(item("a", 1)), listOf(item("b", 1)))
        assertEquals(listOf("b"), r.toWrite.map { it.id })
    }
}
```

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "*SyncMergeTest*" > /tmp/t5.log 2>&1; echo "EXIT=$?" >> /tmp/t5.log; tail -5 /tmp/t5.log`
Expected: 编译失败 unresolved SyncMerge

- [ ] **Step 2: 实现**

```kotlin
package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item

data class MergeResult(val toWrite: List<Item>, val skipped: Int)

/** 冲突策略 = Last-Writer-Wins by updatedAt；平局信 incoming（备份导入语义：恢复优先）。M3 WebDAV 与备份共用。 */
object SyncMerge {
    fun merge(local: List<Item>, incoming: List<Item>): MergeResult {
        val byId = local.associateBy { it.id }
        var skipped = 0
        val writes = incoming.filter { inc ->
            val cur = byId[inc.id]
            val accept = cur == null || inc.updatedAt >= cur.updatedAt
            if (!accept) skipped += 1
            accept
        }
        return MergeResult(writes, skipped)
    }
}
```

- [ ] **Step 3: 全绿 + Commit**

Run: Step 1 命令，Expected `EXIT=0` 6 用例绿。

```bash
git add -A && git commit -m "feat(domain): LWW SyncMerge shared by backup import and future sync"
```

---

### Task 6: 备份 / 恢复 —— JSON 导出导入（红线：数据出路）

**Files:**
- Create: `core/domain/Backup.kt`、`app/src/test/java/com/expirykeeper/domain/BackupTest.kt`
- Modify: `app/build.gradle.kts`（`testImplementation("org.json:json:20240303")`——仅测试 JVM 补 org.json，运行时用 Android 内置）
- Modify: `feature/settings/SettingsScreen.kt`（Task 11 落地 UI；本任务先建 `SettingsScreen.kt` 最小壳 + 备份卡片）
- Modify: `core/data/ItemRepository.kt`（`importMerged` 方法）

**Interfaces:**
- Consumes: Task 5 `SyncMerge.merge`。
- Produces: `object Backup { fun toJson(items: List<Item>): String; fun parse(json: String): List<Item> }`（抛 `BackupFormatException(msg)` 而非返回 null）；`ItemRepository.importMerged(incoming: List<Item>): Int`（返回写入条数）。

- [ ] **Step 1: 失败测试 BackupTest**

```kotlin
package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesAllFields() {
        val items = listOf(
            Item(id = "a", name = "牛奶", categoryId = "food-chilled", reminderKind = ReminderKind.EXPIRY,
                expireAtEpochDay = 20600, reminderOffsetsDays = listOf(2, 0), quantity = 3.0, unit = "盒",
                emoji = "🥛", openedAtEpochDay = 20000, shelfLifeDays = 3, updatedAt = 123),
            Item(id = "b", name = "视频会员", categoryId = "subscription", reminderKind = ReminderKind.RECURRING,
                nextDueAtEpochDay = 20700, recurrenceDays = 30, deletedAt = 999),
        )
        assertEquals(items, Backup.parse(Backup.toJson(items)))
    }

    @Test fun envelopeHasVersionAndCount() {
        val json = Backup.toJson(List(2) { Item(id = "i$it", name = "n", categoryId = "c") })
        assertTrue(json.contains("\"formatVersion\":1"))
        assertTrue(json.contains("\"count\":2"))
    }

    @Test fun garbageInputThrowsTyped() {
        assertThrows(BackupFormatException::class.java) { Backup.parse("{oops") }
        assertThrows(BackupFormatException::class.java) { Backup.toJson(listOf(Item(id = "x", name = "n", categoryId = "c").copy(updatedAt = -5))) }
    }
}
```

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "*BackupTest*" > /tmp/t6.log 2>&1; echo "EXIT=$?" >> /tmp/t6.log; tail -8 /tmp/t6.log`
Expected: unresolved Backup。先加 `testImplementation("org.json:json:20240303")`（白名单内，huaweicloud 镜像有）。

- [ ] **Step 2: 实现 Backup.kt**

```kotlin
package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupFormatException(msg: String) : Exception(msg)

/** 备份信封 {formatVersion, exportedAt, count, items:[...]}；运行时用 Android 内置 org.json，JVM 测试补 org.json:json */
object Backup {
    fun toJson(items: List<Item>): String {
        items.forEach { if (it.updatedAt < 0) throw BackupFormatException("非法时间戳 ${it.id}") }
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("categoryId", it.categoryId)
                put("location", it.location ?: JSONObject.NULL); put("note", it.note ?: JSONObject.NULL)
                put("barcode", it.barcode ?: JSONObject.NULL); put("emoji", it.emoji ?: JSONObject.NULL)
                put("reminderKind", it.reminderKind.name)
                put("expireAtEpochDay", it.expireAtEpochDay ?: JSONObject.NULL)
                put("reminderOffsetsDays", it.reminderOffsetsDays.joinToString(","))
                put("quantity", it.quantity ?: JSONObject.NULL); put("unit", it.unit ?: JSONObject.NULL)
                put("lowStockThreshold", it.lowStockThreshold ?: JSONObject.NULL)
                put("nextDueAtEpochDay", it.nextDueAtEpochDay ?: JSONObject.NULL)
                put("recurrenceDays", it.recurrenceDays ?: JSONObject.NULL)
                put("openedAtEpochDay", it.openedAtEpochDay ?: JSONObject.NULL)
                put("shelfLifeDays", it.shelfLifeDays ?: JSONObject.NULL)
                put("handledAtEpochDay", it.handledAtEpochDay ?: JSONObject.NULL)
                put("handledStatus", it.handledStatus ?: JSONObject.NULL)
                put("snoozedUntilEpochDay", it.snoozedUntilEpochDay ?: JSONObject.NULL)
                put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                put("lastModifiedBy", it.lastModifiedBy ?: JSONObject.NULL)
                put("deletedAt", it.deletedAt ?: JSONObject.NULL)
            })
        }
        return JSONObject().put("formatVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("count", items.size).put("items", arr).toString(2)
    }

    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else optLong(k)
    private fun JSONObject.optIntOrNull(k: String) = if (isNull(k)) null else optInt(k)
    private fun JSONObject.optDoubleOrNull(k: String) = if (isNull(k)) null else optDouble(k)
    private fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else optString(k)

    fun parse(json: String): List<Item> {
        val root = try { JSONObject(json) } catch (e: JSONException) { throw BackupFormatException("不是有效的备份 JSON") }
        if (root.optInt("formatVersion") != 1) throw BackupFormatException("不支持的备份版本")
        val arr = root.getJSONArray("items")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Item(
                id = o.getString("id"), name = o.getString("name"), categoryId = o.getString("categoryId"),
                location = o.optStringOrNull("location"), note = o.optStringOrNull("note"),
                barcode = o.optStringOrNull("barcode"), emoji = o.optStringOrNull("emoji"),
                reminderKind = runCatching { ReminderKind.valueOf(o.getString("reminderKind")) }.getOrDefault(ReminderKind.EXPIRY),
                expireAtEpochDay = o.optLongOrNull("expireAtEpochDay"),
                reminderOffsetsDays = (o.optStringOrNull("reminderOffsetsDays") ?: "3,0").split(",").mapNotNull { it.trim().toIntOrNull() },
                quantity = o.optDoubleOrNull("quantity"), unit = o.optStringOrNull("unit"),
                lowStockThreshold = o.optDoubleOrNull("lowStockThreshold"),
                nextDueAtEpochDay = o.optLongOrNull("nextDueAtEpochDay"), recurrenceDays = o.optIntOrNull("recurrenceDays"),
                openedAtEpochDay = o.optLongOrNull("openedAtEpochDay"), shelfLifeDays = o.optIntOrNull("shelfLifeDays"),
                handledAtEpochDay = o.optLongOrNull("handledAtEpochDay"), handledStatus = o.optStringOrNull("handledStatus"),
                snoozedUntilEpochDay = o.optLongOrNull("snoozedUntilEpochDay"),
                createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
                lastModifiedBy = o.optStringOrNull("lastModifiedBy"), deletedAt = o.optLongOrNull("deletedAt"),
            )
        }
    }
}
```

- [ ] **Step 3: Repository 导入方法**

```kotlin
    /** 恢复入口：与本地 LWW 合并后写入，绝不清空数据库；每条写入照常进 change_log */
    suspend fun importMerged(incoming: List<Item>): Int {
        val result = com.expirykeeper.core.domain.SyncMerge.merge(itemDao.getAll(), incoming)
        result.toWrite.forEach { upsertRaw(it) }
        return result.toWrite.size
    }

    private suspend fun upsertRaw(item: Item) {
        itemDao.upsert(item)
        changeLogDao.insert(ChangeLogEntry(itemId = item.id, op = if (item.deletedAt != null) "delete" else "upsert", updatedAt = item.updatedAt, deviceId = deviceId))
    }
```

- [ ] **Step 4: Settings 壳 + 备份/恢复 UI（SAF，无权限弹窗）**

Create `feature/settings/SettingsScreen.kt`（完整屏在 Task 11，本步先建文件与备份卡）：

```kotlin
package com.expirykeeper.feature.settings

// imports omitted from plan text — implementer writes full list

@Composable
fun SettingsScreen(vm: ItemsViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let {
        scope.launch(Dispatchers.IO) {
            val json = Backup.toJson(vm.snapshotRepo().getAll())
            ctx.contentResolver.openOutputStream(it)?.use { s -> s.write(json.toByteArray()) }
        } } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let {
        scope.launch(Dispatchers.IO) {
            val text = ctx.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
            runCatching { Backup.parse(text) }
                .onSuccess { list -> vm.importBackup(list); android.widget.Toast.makeText(ctx, "已恢复 ${list.size} 条", Toast.LENGTH_SHORT).show() }
                .onFailure { android.widget.Toast.makeText(ctx, "备份文件格式不对，未导入任何数据", Toast.LENGTH_LONG).show() }
        } } }
    // Scaffold(BigHeader("设置", onBack)) { 备份卡片：两按钮挂 export.launch("expiry-keeper-backup.json") / import.launch(arrayOf("application/json")) }
}
```
落地要求：VM 加 `fun importBackup(items: List<Item>) = viewModelScope.launch { repo.importMerged(items); ReminderScheduler.runNow(getApplication()) }`；导出用 io 线程；失败必须 Toast 说明"未导入任何数据"（红线：坏文件不得破坏现有库）。

- [ ] **Step 5: NavHost 挂 settings 路由**

`EkApp.kt`：composable("settings")，顶栏设置入口（Task 7 的 EkTopBar）暂用 Today 屏标题行 ⚙️ IconButton `nav.navigate("settings")`。

- [ ] **Step 6: 验证 + Commit**

Run BackupTest 命令 Expected EXIT=0；再全量 assembleDebug EXIT=0。emulator 手测：设置 → 导出到"下载"→ 改一条数据 → 导入该文件 → Toast 且数据回滚正确。

```bash
git add -A && git commit -m "feat(domain): SAF backup export/import via JSON + LWW merge (data escape hatch)"
```

---

### Task 7: 设计系统 —— Material 3 Expressive 主题 + 组件库

**Files:**
- Create: `core/ui/designsystem/{Color,Type,Shapes}.kt`、`core/ui/designsystem/Components.kt`
- Modify: `core/ui/designsystem/Theme.kt`（重写：动态取色 + 开关）、`gradle/libs.versions.toml`/`app/build.gradle.kts`（material3 已有；确认 `androidx.compose.material:material-icons-extended` 已在依赖）
- Modify: `core/data/AppDatabase.kt` 无关；`App.kt`/prefs 增 `dynamicColor` 布尔（默认 true，API31+ 生效）

**Interfaces:**
- Produces（后续所有 UI 任务只准用这些组件）:
  - `EkTheme(dynamicAllowed: Boolean = true, content)` —— API31+ 且开关开 → `dynamicLight/DarkColorScheme(context)`，否则回落品牌 teal。
  - `StatusTone(status: DueStatus): Pair<Color, Color>`（容器色/内容色映射：OVERDUE·DUE_TODAY→error(-Container)、DUE_SOON·RENEWAL_SOON→tertiaryContainer、LOW_STOCK→secondaryContainer、RENEWAL_TODAY→errorContainer）
  - `StatusPill(status, label)` —— 大圆角胶囊（`RoundedCornerShape(50)`）、labelSmall 加粗
  - `DueRing(daysLeft: Long?, size: Dp = 44.dp)` —— Canvas 圆弧进度：14 天满环→0；中心数字 headlineSmall
  - `SectionHeader(text, count: Int? = null)` —— titleLarge + 尾部 Badge
  - `EmptyState(emoji, title, hint)` —— 居中大 emoji + 文案
  - `ItemCard(item, icon, tone, onClick, onLongClick, trailing: @Composable () -> Unit)` —— shape large `RoundedCornerShape(28.dp)`、`Modifier.borderStroke` 不用、surfaceContainer + 按压 scale 动画
  - `BigHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})` —— displaySmall 大标题（Google Clock 风顶部）
  - `EmojiRow(selected, options, onSelect)` —— FlowRow 实验性 opt-in `@OptIn(ExperimentalLayoutApi::class)`
- Produces: 偏好读写 `AppPrefs`（`core/data/AppPrefs.kt`，包 SharedPreferences）：`var dynamicColor: Boolean`、`var notificationTime: String`（预留）。

- [ ] **Step 1: AppPrefs**

```kotlin
package com.expirykeeper.core.data

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var dynamicColor: Boolean
        get() = p.getBoolean("dynamicColor", true)
        set(v) = p.edit().putBoolean("dynamicColor", v).apply()
}
```
`App.kt` AppContainer 增 `val prefs: AppPrefs`。

- [ ] **Step 2: Theme/Shapes/Type/Color**

```kotlin
// Shapes.kt
package com.expirykeeper.core.ui.designsystem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val EkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
```
Theme.kt 重写：

```kotlin
@Composable
fun EkTheme(dynamicAllowed: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (dynamicAllowed && Build.VERSION.SDK_INT >= 31 && EkPrefsHolder.dynamicAllowed) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, shapes = EkShapes, typography = EkTypography, content = content)
}
```
落地方式：不建 Holder 单例 —— `EkApp`/`MainActivity` 读 `app.container.prefs.dynamicColor` 后以参数传入 `EkTheme(dynamicAllowed = ...)`；Theme.kt 签名即 `fun EkTheme(dynamicAllowed: Boolean = true, ...)`。Type.kt：仅覆盖 `displaySmall`(40sp bold)、`titleLarge`(22sp semiBold)，其余用默认。Color.kt：品牌 teal 常量（沿用现有 0xFF00696D 系）。

- [ ] **Step 3: Components.kt（全部新组件一个文件，>300 行则拆）**

关键实现要求（实现者按此写出完整代码，均为 Material 3 常规组合）：
- StatusPill：`Box(background(tone.container, RoundedCornerShape(50)), padding(h=10.dp,v=5.dp)) { Text(label, color=tone.onContainer, style=labelSmall.copy(fontWeight=SemiBold)) }`
- DueRing：`Canvas` drawArc（背景 stroke surfaceVariant、前景 StatusTone 色、sweep = 360f * daysLeft/14f 夹 0..1）+ 中心 `Text`；daysLeft==null 画"·"占位（LOW_STOCK）
- ItemCard：`Card(shape=large, colors=surfaceContainer(lowest), modifier.scale(按 pressureAnim))`，leading 44dp emoji 文本（fontSize 26sp），trailing slot 放 StatusPill/DueRing
- BigHeader：Row{ 可选 Back IconButton + Column{ Text(title, displaySmall) + subtitle } + Spacer(weight) + actions }，顶部 padding 24dp
- EmptyState：Column centerAligned，emoji 64sp + titleLarge + bodyMedium hint，垂直留白 48dp
- EmojiRow：`FlowRow(horizontalArrangement=spacedBy(6.dp))` chips `onSelect`。

- [ ] **Step 4: 三旧屏最小适配**

旧屏暂只替换 import 与新 Theme 生效（不重构布局 —— Task 8-10 做）：确认 `assembleDebug` EXIT=0；emulator 快速翻看：动态色生效（换壁纸主色验证）、暗色无对比度错误（OVERDUE 红仍可读）、卡片圆角变大。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ui): M3 expressive design system — dynamic color, shapes, status pill, due ring, big header"
```

---

### Task 8: 今日屏 v2 —— Hero 统计 + 分组卡 + 快速操作

**Files:**
- Modify: `feature/today/TodayScreen.kt`（重写）、`ui/ItemsViewModel.kt`（挂 rollForward/markHandled/snooze + 今日 reminders StateFlow）

**Interfaces:**
- Consumes: designsystem 组件、Task 4 引擎、Task 3 repo 快速操作。
- Produces: `ItemsViewModel.reminders: StateFlow<List<Reminder>>`（computeForDate(items, today)）；`ItemsViewModel.statsToday`（today/upcoming14 计数）；快速操作后自动 `ReminderScheduler.runNow` 重排闹钟并取消既有通知：`NotificationHelper.cancelFor(vm, reminder)`。

- [ ] **Step 1: VM 扩展**

```kotlin
    private val today get() = java.time.LocalDate.now()
    val reminders: StateFlow<List<Reminder>> = items.map {
        ReminderEngine.computeForDate(it, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rollForward(id: String) = viewModelScope.launch {
        if (!repo.rollForward(id, today)) { openEditorInstead.value = id } // 无法滚期 → 提示跳编辑
        repo.getById(id)?.let { /* cancel notif */ }
        NotificationHelper.cancelItem(getApplication(), id)
        ReminderScheduler.runNow(getApplication())
    }
    fun markHandled(r: Reminder) = viewModelScope.launch {
        repo.markHandled(r.item.id, r.status.name, today)
        NotificationHelper.cancel(getApplication(), r.notificationId)
    }
    fun snooze3(r: Reminder) = viewModelScope.launch {
        repo.snooze(r.item.id, 3, today)
        NotificationHelper.cancel(getApplication(), r.notificationId)
        ReminderScheduler.runNow(getApplication())
    }
```
`NotificationHelper` 增 `fun cancel(ctx, id)`（NM.cancel(id)）与 `fun cancelItem(ctx, itemId)`（对 6 种 status × 近 2 天 epochDay 组合 cancel —— 简单做法：按 notifId 纯函数重算 cancel，覆盖 DUE_SOON/TODAY/OVERDUE）。

- [ ] **Step 2: 布局重写**

结构（全部用 Task 7 组件）：

```
BigHeader("今日", subtitle = "9月22日 · 周一") { IconButton(settings ⚙️) }
Hero 卡（ItemCard 变体，surfaceContainerHigh，padding 24）：
  Row: 三列统计 数字(displaySmall) + 标签(bodySmall) —— 待处理 reminders.size / 两周内 upcoming.size / 全部 items.size
  文案：reminders 为空 → "今天一切安好 ✨"
SectionHeader("紧急", urgent.size)      // OVERDUE + DUE_TODAY
  ItemCard: leading emoji | 名称+品类·位置 | trailing DueRing(daysLeft)
  卡下方动作行（仅紧急组显示，AnimatedVisibility）：
    AssistChip 🍽 续期 / AssistChip 😴 稍后3天 / AssistChip ✅ 今天不再提醒
    （"续期"返回 false 时 Toast "这件没有保质期或周期规则，去编辑里补上" + 点击跳编辑）
SectionHeader("即将到期", soon.size)     // DUE_SOON + RENEWAL_SOON
SectionHeader("需要关注", others.size)   // LOW_STOCK + RENEWAL_TODAY
if 全空 → EmptyState("🌿", "今天没有要处理的事", "去清单看看，或添加新物品")
```
- onClick ItemCard → `nav.navigate("detail/$id")`；长按 → 同样进详情。
- 列表动画：`animateItem()` on item cards；分组折叠非必需。

- [ ] **Step 3: 验收（emulator 手测清单）**

1) 无数据：EmptyState 渲染；2) 造 3 条（明天到期/已过期/低库存）：分组正确、DueRing 数字正确；3) 点"✅ 今天不再提醒"卡片即时消失、重进 App 不出现、（次日逻辑由单测保证）；4) "稍后3天"同上；5) "续期"对有 shelfLife 的 EXPIRY：到期日变为 today+shelfLife，卡片消失；6) 暗色模式全页无不可读文本。

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(ui): today v2 — hero stats, grouped urgent sections, quick actions (roll/snooze/handle)"
```

---

### Task 9: 清单屏 v2 —— 搜索 + 排序 + 分组吸顶 + 长按详情

**Files:**
- Modify: `feature/list/ListScreen.kt`（重写）、`ui/ItemsViewModel.kt`（query/sort 状态）

**Interfaces:**
- Produces: `enum class ItemSort { EXPIRE_ASC, NAME, CREATED_DESC, CATEGORY }`（放 core/domain）；`ItemsViewModel.filterQuery: MutableStateFlow<String>`、`sortOrder`；`fun deleteWithUndo(id)` —— 删除 + Snackbar "已删除《x》" + 动作"撤销"（撤销 = repo.save(原 item)，因 updatedAt 更大自然复活墓碑）。

- [ ] **Step 1: 派生过滤流**

```kotlin
    val visibleItems: StateFlow<List<Item>> = combine(items, filterQuery, sortOrder) { list, q, sort ->
        list.asSequence()
            .filter { q.isBlank() || it.name.contains(q, true) || (it.note?.contains(q, true) ?: false) || (it.location?.contains(q, true) ?: false) }
            .let { seq ->
                when (sort) {
                    ItemSort.EXPIRE_ASC -> seq.sortedBy { ReminderEngine.effectiveExpireDay(it) ?: Long.MAX_VALUE }
                    ItemSort.NAME -> seq.sortedBy { it.name }
                    ItemSort.CREATED_DESC -> seq.sortedByDescending { it.createdAt }
                    ItemSort.CATEGORY -> seq.sortedBy { it.categoryId }
                }
            }.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

- [ ] **Step 2: 布局**

```
BigHeader("清单", subtitle "${visible.size} 件") { 排序 IconButton → DropdownMenu 4 项 }
SearchBar（M3 标准）绑定 filterQuery
分组模式（sortOrder==CATEGORY 时按品类分组，否则单组"全部"）：
  StickyHeader（LazyColumn + 自算：items 已含组首标记字段，或直接非吸顶的 SectionHeader —— MVP 用非吸顶分组头即可，验收不卡）
  每组前 SectionHeader(品类emoji+name, count)
  ItemCard: trailing = StatusPill(最近一次 computeOne 结果) 或 无提醒 → quantity 文本 "x 盒"
onClick/longClick → detail/$id
```
删除入口移到详情屏（Task 11），ListScreen 不再放删除按钮。

- [ ] **Step 3: 验收清单**

1) 搜索"牛奶"实时过滤、清空恢复；2) 4 种排序切换顺序正确（无到期日者沉底）；3) 分组计数与明细一致；4) 大数据手测：模拟器造 100 条（设置页加临时"填充演示数据"按钮？—— 不加。用 adb 连跑脚本超范围，跳过；流畅性以 30 条手测无 jank 为准）。

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(ui): list v2 — search, 4-way sort, category grouping, delete moved to detail (undo-ready)"
```

---

### Task 10: 添加/编辑 v2 —— emoji 选择器 + 开瓶保质期推导 + 表单分层

**Files:**
- Modify: `feature/addedit/AddEditScreen.kt`（重写）、Create `feature/addedit/EmojiPicker.kt`
- Create: `core/domain/EmojiSet.kt`

**Interfaces:**
- Produces: `object EmojiSet { val byCategory: Map<String, List<String>> }`（key=品类 id + "other"，每组 12~16 个，覆盖：冷藏🥛🧈🥚🧀🍄🥗…、冷冻🧊🍟🥟🍣🍦…、干货🍪🍜🥜☕🍫…、药品💊🩹🧴🩺…、化妆🧴💄🧼🪮🌸…、数码🔌📱💻🖥🎧⌚…、证件🪪📇🛂📜🏠🚗…、订阅🔁📺🎬🎵☁️🏋️📰…、耗材🧻🧽🧼🪣🔋🧯…、other📦🎁🧸🪴🔧🍂⚙️）
- Consumes: Task 3 `Item.emoji/openedAtEpochDay/shelfLifeDays`；保存走 `repo.save`（派生到期）。

- [ ] **Step 1: 表单结构（四段式 Card，替代裸 Column）**

```
段1 选品类：横滚 EmojiRow(chips)（9 品类，选中高亮 secondaryContainer）
段2 名称+emoji：OutlinedTextField(名称) + 行内按钮 [当前图标 emoji 大 28sp ▾] → 弹 ModalBottomSheet(EmojiPicker(sheet 内 EmojiRow(byCategory[cat]) + "更多" other))
段3 到期规则（按 reminderKind 分支）：
  EXPIRY: SegmentedButton [到期日期 | 开封+保质期]；
    日期模式 → 沿用 DatePicker；
    开封模式 → 开封日期(DatePicker, 默认今天) + 保质期快捷 chips(品类 defaultShelfLifeChoicesDays + "自定义"数字输入) → 预览文本 "预计 2026-10-01 到期"
  CONSUMABLE: 数量+单位+阈值（现三输入移入 Card，逻辑不变）
  RECURRING: 下次日期 + 周期天数 chips(30/90/365/自定义)
段4 备注/位置/条码：可折叠"更多设置"（AnimatedVisibility）
保存校验：名称必填；EXPIRY 两模式至少一个有值；开封模式 shelfLife∈1..3650 —— 不满足时 errorText 内联红字，禁用主按钮（不 Toast）。
```

- [ ] **Step 2: EmojiPicker.kt**

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerSheet(current: String?, category: String, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // 首行："跟随品类"（onPick(null)）+ 品类组 + other 组，EmojiRow 复用
    }
}
```

- [ ] **Step 3: 编辑回填**

`itemId != null` 时：`LaunchedEffect` getById → 全部字段回填（含 emoji、openedAt/shelfLife 双模式判断：expireAtEpochDay 为 null 且 shelfLifeDays 非 null → 开封模式）。

- [ ] **Step 4: 验收清单**

1) 品类→图标→规则三步零键盘完成一次添加（"牛奶，开封 3 天"路径 ≤15 秒）；2) 自定义 emoji 🐠 保存后今日/清单/详情/通知标题全部显示；3) 校验：空名/双空规则保存按钮禁用且提示明确；4) 编辑旧数据不丢字段（尤其 M1 时期无 emoji 的物品打开不崩）。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ui): add/edit v2 — emoji picker, opened-date+shelf-life derivation, segmented expiry modes, card sections"
```

---

### Task 11: 详情 BottomSheet + 设置屏完整体

**Files:**
- Create: `feature/detail/DetailSheet.kt`
- Modify: `feature/settings/SettingsScreen.kt`（重写为完整设置：权限卡 + 动态色开关 + 备份卡(Task 6 已有则移入) + 数据概览 + 关于）
- Modify: `EkApp.kt`（路由 `detail/{id}`；detail 以 `ModalBottomSheet` 覆盖在来路屏上：route 注册 + `DynamicHost` 或简化 —— 采用 `composable("detail/{id}") { DetailSheet(...) }` 全屏透明容器 + 底部卡片样式即可，不引入库）
- Modify: `ui/ItemsViewModel.kt`（detail StateFlow、deleteWithUndo、recentEvents、overview 计数）

**Interfaces:**
- Produces: 详情数据 = `observeById`（Task 3）+ `ReminderEngine.computeOne` 状态；`RecentEvents = repo.recentEvents(30)` + itemId→name 映射。

- [ ] **Step 1: DetailSheet 内容**

```
ModalBottomSheet:
  头部 Row: emoji 44sp + 名称 titleLarge + 品类·位置 bodySmall
  StatusPill（当前状态）或 "节奏正常"
  键值列表：到期日/剩余天数、开封日+保质期、下次续费+周期、数量/阈值、条码、备注
  动作按钮行（同今日屏快速操作三件套）
  "最近记录"：该 item 30 天内 events 流水（kind→图标文案映射：add 添加、roll 已续期、snooze 延后、handle 已处理、delete 删除）
  底部：删除（FilledTonalButton error 色）→ vm.deleteWithUndo → Snackbar(undo)
```

- [ ] **Step 2: SettingsScreen 完整体**

```
BigHeader("设置", onBack)
卡1 提醒权限状态：POST_NOTIFICATIONS 授权?、精确闹钟 canScheduleExact?（读 AlarmManager）两行 StatusPill ✅/⚠️；⚠️ 行点击 → 跳系统设置页（ACTION_APP_NOTIFICATION_SETTINGS / ACTION_REQUEST_SCHEDULE_EXACT_ALARM）
卡2 外观：动态取色 Switch(prefs.dynamicColor，切换后 recreate 主题 —— VM 无状态则 LaunchedEffect 重读)
卡3 数据：导出备份 / 恢复备份（Task 6 实现移入）+ 最近导出时间提示（可选）
卡4 概览：总件数、30 天处理次数（rollCount30d）、品类分布 emoji+数量 横排
卡5 关于：名称"到期管家（工程版）"、版本 = BuildConfig.VERSION_NAME、M1→v2 说明
```

- [ ] **Step 3: deleteWithUndo（VM）**

```kotlin
    fun deleteWithUndo(id: String) = viewModelScope.launch {
        val original = repo.getById(id) ?: return@launch
        repo.softDelete(id)
        NotificationHelper.cancelItem(getApplication(), id)
        ReminderScheduler.runNow(getApplication())
        _snackbar.emit(SnackbarMsg("已删除《${original.name}》", "撤销") { repo.save(original) })
    }
```
`_snackbar = MutableSharedFlow<...>`；EkApp Scaffold 挂 SnackbarHost 消费。撤销成功条件：original.updatedAt 已被 repo.save 刷新 → 大于墓碑 → SyncMerge/列表查询（deletedAt IS NULL 重新可见）。

- [ ] **Step 4: 验收清单**

1) 清单长按 → 详情浮层数据正确；2) 删除 → Snackbar 撤销 → 物品回归且到期日不变；3) 权限卡两行真实反映模拟器开关（模拟器通知默认开）；4) 动态色开关立即整体变色/回落 teal；5) 概览计数与手工清点一致。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ui): detail sheet + full settings (permission status, dynamic color, backup, overview, undo-delete)"
```

---

### Task 12: 通知 v2 —— 渠道分组 + 摘要 + 动作按钮

**Files:**
- Modify: `notifications/NotificationHelper.kt`、`notifications/DailyScanWorker.kt`/`ReminderAlarmReceiver.kt`（发多通知处 → 组摘要）

- [ ] **Step 1: 渠道组**

```kotlin
    private const val CHANNEL = "expiry_reminders"
    private const val GROUP_KEY = "com.expirykeeper.GROUP"
    // createChannel 后：nm.createNotificationChannelGroup(NotificationChannelGroup("household", "到期提醒"))
    // channel.groupId = "household"
```

- [ ] **Step 2: 摘要 + 单条**

多条待发时：builder 全部 `setGroup(GROUP_KEY)`，另发 `NotificationCompat.Builder(..., CHANNEL).setGroupSummary(true).setSmallIcon(ic_notification).setContentTitle("到期管家 · ${n} 件事需要处理").setContentText(前 3 条名称.joinToString()).setAutoCancel(true)`；单条时不加 summary。

- [ ] **Step 3: 动作按钮**

每条提醒加 `addAction(0, "稍后 3 天", snoozePendingIntent(itemId))` —— PendingIntent 指回 Receiver：新 action `ACTION_SNOOZE`，onReceive 调 `repo.snooze(id,3,today)`+cancel 该通知（goAsync 模式同 DailyScan）。

- [ ] **Step 4: 验证 + Commit**

assembleDebug + 旧 12 测试绿；emulator：造 4 条到期 → 通知栏聚合成组、点开列表正确、点"稍后 3 天"通知消失且今日屏同步消失。

```bash
git add -A && git commit -m "feat(notifications): group summary + snooze action + channel group"
```

---

### Task 13: 全面体检 —— lint / 测试 / 冷启动 / 文档收尾

**Files:**
- Modify: `docs/PROGRESS.md`（v2 里程碑勾选、新增"v2 验收记录"）、spec §9 状态行

- [ ] **Step 1: 静态检查**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --continue > /tmp/final.log 2>&1; echo "EXIT=$?" >> /tmp/final.log; grep -E "EXIT|error|warning: " /tmp/final.log | tail -20`
Expected: lint 无 Error 级问题（Warning 可留，逐条判断是否低级错误）；全部单测绿。

- [ ] **Step 2: 冷启动与泄漏粗检**

emulator：`adb shell am force-stop com.expirykeeper` → 打开（无日志崩溃）→ 设置 → 备份导出 1 条验证文件 → 强杀重开数据完整。Android Studio Profiler 打开 5 分钟（或 `adb shell dumpsys meminfo com.expirykeeper` 间隔采样 3 次），Native+Graphics 增长 <20% 即过。

- [ ] **Step 3: androidTest 迁移测试（emulator）**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:connectedDebugAndroidTest > /tmp/t13.log 2>&1; echo "EXIT=$?" >> /tmp/t13.log; tail -8 /tmp/t13.log`
Expected: RoomMigrationTest 绿（这是"升级不丢数据"红线的直接证据）。

- [ ] **Step 4: 全链路手测回归（一次性过 Task 8/9/10/11/12 的验收清单）**

任何一条不过 → 回到对应任务修复，禁止带伤收尾。

- [ ] **Step 5: 文档 + 最终提交**

PROGRESS.md：M1 勾掉"真机验收"改为"v2 emulator 验收"记录日期与结果；新增 v2 段落列出 13 任务完成态与遗留（M3 同步、M2 条码仍 pending）。

```bash
git add -A && git commit -m "docs: v2 acceptance record + progress closeout"
```

---

## Self-Review 结论（已执行）

1. **Spec §9 覆盖**：红线1稳健→T3 迁移测试/T4 引擎测试/T5/T6 备份/T13 体检；红线2美观→T7 设计系统 + 各屏重写；红线3丰富→T3 events、T8 快速操作、T9 搜索排序、T10 emoji、T11 概览、T12 通知动作；红线4工程→T1 git+CODESTYLE、T2 分层。M2 条码/M3 同步刻意不在本计划（spec 里程碑仍有效）。
2. **占位符扫描**：Task 7 Step 3 组件内部实现与 Task 10 段式表单给出的是"落地要求 + 结构"，属 UI 组装描述而非逻辑留白，实现者按组件 API 常规写法可完成；核心数据/领域/迁移代码均为全文。
3. **类型一致性**：`effectiveExpireDay/displayIcon/rollForward/markHandled/snooze/SyncMerge.merge/Backup.toJson|parse/importMerged/observeById/StatusPill/DueRing/BigHeader/ItemCard/SectionHeader/EmptyState/EmojiRow/ItemSort` 已跨任务比对签名与包路径（domain 层全部位于 `com.expirykeeper.core.domain`，data 层 `com.expirykeeper.core.data`）。
