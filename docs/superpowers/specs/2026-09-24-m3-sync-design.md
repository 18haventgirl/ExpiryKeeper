# M3 同步 设计规格

日期：2026-09-24　状态：待用户评审
上游文档：`docs/superpowers/specs/2026-09-22-expiry-keeper-design.md` §8（同步一期设想）
进度记录：`docs/PROGRESS.md`（M3 起手清单）

## 1. 一句话目标

让家里几台手机上的清单**在没有自建服务器的前提下最终一致**：谁改的都不丢，删掉的不会复活，冲突的那一份看得见。

M3 只交付 **LocalFolderDriver**（共享目录 = 一个 SAF 目录，坚果云/NAS 的客户端会把它同步到每台设备）。WebDAV 直连驱动留给 M4，接口形状在本规格里定死，届时不返工。

## 2. 决策记录

四条已由用户拍板（2026-09-24 问答），本规格照此展开；其余为本规格的新增决定，逐条给理由。

| 决策 | 结论 | 谁定的 / 原因 |
|---|---|---|
| 同步触发 | 手动「立即同步」+ 打开 App 时自动一次 | **用户已定**。不引入推送、不长连接、不占后台电量 |
| 冲突策略 | 整行 Last-Writer-Wins，**被丢掉的那一份记入历史** | **用户已定**。字段级合并需要 diff/merge 三向状态机，家庭场景收益不抵复杂度 |
| 目录拓扑 | 拓扑 A：**每设备一个全量快照文件**，永不写别人的文件 | **用户已定**。单共享文件的读-改-写在 SAF/WebDAV 上没有 CAS 也没有锁，必然丢更新 |
| 条码 | 彻底不做，M2 收口 | **用户已定**（见 PROGRESS M2 段） |
| 载荷格式 | **复用 `Backup` 信封**，只加一个可选 `deviceId` 字段 | 本规格新增：一份 JSON 同时是"备份"和"快照"，少一个格式就少一类"哪个才是权威"的问题；`Backup.parse` 的严格校验（整文件先验后写）直接继承 |
| 增量 or 全量 | **全量快照，不传 change_log、不同步游标** | 本规格新增，见 §3 |
| 墓碑回收 | **不自动清理** | 本规格新增，见 §7.3 |
| 冲突记录留存 | 滚动保留最近 200 条 | 本规格新增：唯一需要上限的地方，因为它是纯日志、没有回收语义 |

## 3. 为什么是全量快照，不是 change_log 增量

`change_log` 表已经在了（每次写都追加一行，至今**只写不读**），看起来正适合做增量同步。不做，三个理由：

1. **游标是持久化的坑**。增量同步要求每台设备记住"我看到这个目录的哪个位置了"。这个游标一旦丢（卸载重装、清数据、快照回滚——本项目 2026-09-24 刚亲历一次），要么永久漏数据，要么得回头全量拉，等于还是要全量。
2. **增量把收敛性变难**。全量快照 + "取每行在总序下的最大值"是一个**幂等、可交换、可结合**的运算：任何顺序、任何次数重放同一批文件，结果都一样。增量的到达顺序会实打实影响结果。
3. **规模根本不需要**。家庭清单量级是几十到几百条，一行 JSON 约 400 B，一台设备的全量快照几十 KB。同步一次读 N 台设备 × 几十 KB，N 在家庭场景 ≤ 5。省这个流量的收益为零，代价却是上面两条。

结论：`change_log` 继续只写不读，作为本地审计与 M4 的候选原料。本规格不动它。

## 4. 拓扑与文件

```
<用户选定的共享目录>/
  expiry-keeper-snap-<deviceIdA>.json     ← A 只写这一个
  expiry-keeper-snap-<deviceIdB>.json     ← B 只写这一个
  expiry-keeper-backup-2026-09-24.json    ← 手动备份，同步不读（文件名前缀不匹配）
```

- `deviceId` 沿用 `App` 启动时生成并持久化在 SharedPreferences 里的 UUID（`App.kt:26-29`），**每台设备一个，卸载重装即换新**——重装后老快照文件会留在目录里成为孤儿，见 §7.4。
- 文件名字符集：`expiry-keeper-snap-` + UUID（`[0-9a-f-]`）+ `.json`，全部 SAF 合法字符，不做转义。
- 一台设备**永远只写自己命名的那个文件**。这把"并发写"从需要锁的问题，变成不存在的问题。

## 5. 载荷格式

在 `Backup` 的信封上做一次向后兼容的小扩展：

```json
{
  "formatVersion": 1,
  "exportedAt": 1758729600000,
  "count": 7,
  "deviceId": "f4d1…",              // 新增，选填；parse 忽略未知字段
  "items": [ { …含 deletedAt / lastModifiedBy… } ]
}
```

- `Backup.toJson(items)` 与 `Backup.parse(json)` 原样复用；新增一个 `toJson` 的可选 `deviceId` 参数与一个读 `deviceId` 的 `metaOf(json)`，**不新增第二套解析器**。
- **同步必须带墓碑**：快照用 `itemDao.getAllIncludingTombstones()` 生成。现有 `repo.getAll()` 在 SQL 层就 `deletedAt IS NULL`，照抄过来删除永远传不出去（PROGRESS 遗留 ⑤ 的真相）。手动备份保持现状（时间点还原，还原不该重放删除）。
- `formatVersion != 1` 或任何一行解析失败 → **整个文件丢弃**，一条都不写。这是 `Backup.parse` 已有的严格性（v2 终审 C2），同步继承它：宁可这次没同步上，也不把外来版本的半截数据落库。

## 6. 合并算法

### 6.1 一次同步 = 拉取全部别人的快照 → 合并 → 发布自己的

```
syncOnce():
  docs = driver.list().filter { deviceIdOf(it) != selfId }.sorted()   // 排序：让各设备看到同一序列
  for name in docs:
     text = driver.read(name)                       // IO 失败 → 记 failed，继续下一个
     items = Backup.parse(text)                     // 解析失败 → 记 failed，继续下一个
     repo.mergeFromSync(items)                      // 冲突者落入 sync_conflicts
  driver.write(ownName, Backup.toJson(repo.getAllIncludingTombstones(), selfId))
```

先拉后推：这一轮学到的东西立刻进到自己发布的快照里，等于一次 gossip，两台设备之间一次同步就收敛，不需要"再同步一次"。

### 6.2 LWW 的平局必须打破（M3 的第一个真 bug）

现状（`SyncMerge.kt:14`）：

```kotlin
val accept = cur == null || inc.updatedAt >= cur.updatedAt
```

`>=` 的意思是"平局信 incoming"。这在**手动导入单个备份**时无所谓（信刚拿到的那份是合理直觉，`SyncMergeTest.tiePrefersIncoming` 就是钉这个语义的），在同步里是收敛性缺陷：设备 1 按 A→B 的顺序合，设备 2 按 B→A 的顺序合，同一毫秒的两版会各自留下不同的赢家，然后各自发布不同的快照 → 两台设备来回摆动，永远不一致。

两个调用方的意图**本来就不同**，不强行统一，把平局策略做成显式参数：

```kotlin
enum class OnTie { PREFER_INCOMING,  // 手动恢复：信手上这份（现状语义，测试不动）
                   DEVICE_ORDER }    // 同步：按 deviceId 定序，保证各设备算出同一赢家

fun merge(local: List<Item>, incoming: List<Item>, onTie: OnTie = OnTie.PREFER_INCOMING): MergeResult
```

同步路径下的比较是 `(updatedAt, lastModifiedBy)` 的**字典序全序**：

```kotlin
private fun wins(inc: Item, cur: Item): Boolean =
    inc.updatedAt > cur.updatedAt ||
    (inc.updatedAt == cur.updatedAt && (inc.lastModifiedBy ?: "") > (cur.lastModifiedBy ?: ""))
```

- `lastModifiedBy` 为 null（v1 老数据、种子数据）→ 视作 `""`，排在任何真实 deviceId 之前，不会因此丢数据。
- 平局时谁赢取决于 UUID 字符串比较——**任意但确定**。全平台所有设备算出同一个赢家，这才是要的性质，选谁当赢家本身无所谓。
- `importMerged`（手动恢复）默认 `PREFER_INCOMING`，行为与今天逐字节一致；`mergeFromSync` 显式传 `DEVICE_ORDER`。
- 同步路径下合并因此成为"逐行取总序最大值"：幂等、可交换、可结合 → §3 第 2 条的收敛性论证成立，且可用测试钉住（§9.1）。
- 入参自身先去重（`importMerged` 现有逻辑：同 id 取最新一版）也要跟着换成同一个总序比较器，否则一份快照里出现同 id 两行时又会引入一处顺序依赖。

### 6.3 三种结局，不能只有两种

`>=` 改成严格比较之后会多出一类：同一份数据被重放（自己上次发布的快照绕回来、或同一目录被同步两次、或用户把同一个备份文件导入两次），此时 `inc == cur` 逐字段相等。

若把它算成"被 LWW 拒绝"，每次同步都会为每条未变记录写一条冲突历史——噪声直接淹没真正需要看的冲突。所以 `MergeResult` 从 `(toWrite, skipped)` 变成三桶：

```kotlin
data class MergeResult(
    val toWrite: List<Item>,          // incoming 胜出 → 落库
    val identical: Int,               // 与本地逐字段相等 → 什么都不做，不计冲突
    val rejected: List<Dropped>,      // 真正的冲突 → 记入历史
) {
    val skipped: Int get() = identical + rejected.size   // 保住现有读法（importMerged 与断言 r.skipped）
}
data class Dropped(val kept: Item, val loser: Item)   // loser = 被丢掉的那一份
```

- **判定顺序：先结构相等，再看平局策略。** `identical` 对两个调用方都成立，不分 `OnTie`。
- **一处用户可见的行为变化（主动接受）**：重复导入同一份备份，Toast 从"导入 7 条"变成"导入 0 条 · 跳过 7 条"。这才是真话——第二次确实什么都没改。同步后需同步更新 PROGRESS 的人工验收清单第 1 项。
- **两个方向都记冲突**：incoming 赢时本地那份被丢，才是用户最想知道的"我手机上改的东西被覆盖了"；只记"被拒绝的 incoming"会漏掉这半边。逐字段相等的重放不记。

## 7. 数据安全红线（对齐用户红线 ①）

这七条每一条都要变成测试或代码审查项，不是口号。

**7.1 同步永不硬删。** 代码里不允许出现 `DELETE FROM items`。删除只是 `deletedAt` 字段的一次 LWW 更新。

**7.2 一个坏文件不影响好文件。** 目录里 5 个快照，3 个坏 2 个好 → 那 2 个照常合并，3 个整份丢弃并计入 `failed`。绝不"尽力解析能读多少算多少"。

**7.3 墓碑不自动回收。** 没有清理策略是对的：一旦清掉墓碑，"A 删了、B 离线又改"就会让已删的东西复活，而这个 bug 只在两台设备时间错开时才出现，几乎不可能被测出来。代价是列表里有隐形行——`items` 行数随删除累计，家庭场景的量级（千级）远不到需要担心的程度。真要做，也是 M4 之后带一个"最近 90 天之内的墓碑才作数"的显式策略。

**7.4 孤儿快照不删。** 别人卸载重装换了 UUID，旧文件留在目录里继续被合并——**这是正确的**：那些行有自己的 `lastModifiedBy`，LWW 只看时间戳，新旧 UUID 之间照常收敛。删它反而危险（可能删掉某台设备唯一的一份数据）。目录变脏由 UI 上的设备列表可见（§8），用户想清就自己去文件管理器清。

**7.5 单飞。** 手动按钮与开屏自动可能撞车，用一个 `Mutex.tryLock` 保证同时只有一个 `syncOnce`；后到的直接返回"正在同步"，不排队（排队的第二轮读到的还是同一批文件，没有新信息）。

**7.6 失败静默、成功留痕。** 任何 IO/解析失败都不弹拦截式对话框、不发通知，只在 Snackbar 与设置页的"上次同步"里如实报数（写入 N、跳过 M、失败 K）。同步失败绝不能让用户觉得数据丢了。

**7.7 M3 不碰任何凭证。** 只有一个本机 SAF 目录 URI（`takePersistableUriPermission`）。没有 token、没有密码、没有服务器地址，因此没有"凭证存在哪"的问题。WebDAV 凭证是 M4 的事，届时必须走 `EncryptedSharedPreferences`/Keystore，且**永不进 `Backup` 导出**（否则一个备份文件就泄露了一次凭证）——这条现在就写进规格，防止 M4 顺手把 prefs 一起导出。

## 8. UI（对齐用户红线 ②）

设置在"数据"与"概览"之间插入一张卡：

```
同步
  共享目录        未设置 / 已选择：坚果云/ExpiryKeeper › 更改
  立即同步        ────────────────────────▶  Snackbar：写入 3 · 跳过 41 · 失败 0
  上次同步        9月24日 15:41（dateZh 统一格式）
  目录中的设备    f4d1c2…（本机）· 8ab03e… · 5 分钟前
  冲突记录 (2)    ›
```

- 复用 `EkCard` / `KeyValueRow` / `Pill`，不新增组件；设备 id 显示前 6 位 + `Pill` 标"本机"。
- 冲突列表页（新路由 `sync-conflicts`，次级页走 `BigHeader`）：每条一行——物品名、被丢版本的到期日或数量、丢弃时间、来自哪台设备。只读 + 底部"清空记录"。**不做**"恢复这一份"按钮：恢复会写一条新记录，用户以为撤销了冲突，实际是又一次 LWW 覆盖，容易误解；真要拿回来，去手动导入那份备份。
- 目录未设置时，开屏自动同步静默跳过，不弹提示、不引导（首次用户可能压根不想同步）。
- 空态、暗色、48dp 触控区沿用 UI-AUDIT 之后的规范。

## 9. 测试策略

### 9.1 JVM 单测（纯函数，主体在这里）

`SyncMerge` 换成总序后，性质测试优先于示例测试：

1. **收敛/顺序无关**：构造 3 台设备各自的 items，以全部 6 种到达顺序合并 → 断言最终状态完全相同（这是 §6.2 存在的理由，也是它唯一的证明）。
2. **幂等**：同一批 docs 连合两轮 → 第二轮 `toWrite` 为空、`identical` 等于总行数、`rejected` 为空（钉住 §6.3 的重放噪声问题）。
3. **同毫秒不同设备**：`updatedAt` 相同、`lastModifiedBy` 不同 → `DEVICE_ORDER` 下两台设备算出同一赢家；`PREFER_INCOMING` 下仍然信 incoming（`tiePrefersIncoming` 现有测试保持绿色，它是恢复语义的护栏）。
4. **删除 vs 编辑**：晚于删除的编辑复活它；早于删除的编辑被墓碑盖掉（v2 终审 C1 的两条，迁到总序语义下重测）。
5. **null `lastModifiedBy`** 输给任何真实 deviceId，且不会因此丢行。
6. **冲突记录方向**：incoming 赢时 `Dropped.loser` 是本地那份；内容相同的重放不产生 `Dropped`。
7. **`Backup` 往返**：带 `deviceId` 与墓碑的快照 `toJson → parse` 无损；`deletedAt` 非空的行解析回来仍非空（防"同步丢删除"）。

### 9.2 Room / 插桩

- 迁移 v2→v3 测试（沿用 `RoomMigrationTest` 的 `MigrationTestHelper` 模式）：旧库灌数据 → 迁移 → 断言行数与内容不变、新表存在。**只对新增表做 `CREATE TABLE`，不动 `items`**，所以迁移风险本身被压到最低。
- `mergeFromSync` 的落库路径：一次导入含墓碑的快照后 `getAll()`（过滤视图）少一条、`getAllIncludingTombstones()` 不少。

### 9.3 端到端（双设备仿真）

一个 AVD 装两台不同签名不行，所以沿用 M1 就定下的"**双目录仿真两设备**"：同一进程内跑两个 `SyncDriver`，各自指向 `dirA`/`dirB`（内容相同的两份目录，手工搬运 = 网盘同步），中间用两套内存 item 集合。这一步用 JVM 单测即可覆盖（driver 用假实现注入），**不需要真两台手机**。

真机验收留给用户（红线：只在 Studio 模拟器上测）：两台 AVD（`Pixel_9` + 为 M3 新建的第二台，不与 Studio 抢同一台）指向 `Download/ExpiryKeeper`，A 加一条 → B 同步 → B 看到；B 删一条 → A 同步 → 消失且不复活；两台同时改同一条 → 冲突记录 2 条以内、内容正确。

## 10. 交付边界

**M3 做**：`SyncDriver`（list/read/write）+ `LocalFolderDriver`(SAF) + 全量快照格式 + 总序 LWW 合并 + 冲突历史表与页 + 手动/开屏触发 + 上述测试。

**M3 明确不做**：WebDAV 驱动与凭证存储、字段级合并、冲突"恢复这一份"、墓碑回收、多设备实时推送、端到端加密（快照明文放在网盘里，暴露的是"家里买了什么牛奶、什么时候到期"——见 §11 的取舍）、change_log 的任何用途、账号体系。

## 11. 已知取舍与风险

| 风险 | 影响 | 态度 |
|---|---|---|
| 快照明文存在第三方网盘 | 物品名/数量/到期日泄露给网盘账号 | 家庭清单的敏感度低于照片和通讯录；加密需要用户管密钥，一旦忘光 = 全家数据作废（违反红线 ①）。**不加密**，但在设置页目录下方留一行说明文案 |
| 时钟漂移 | 设备快 5 分钟 → 它的写入在 5 分钟内"永远赢" | 不做向量时钟/逻辑时钟：家庭设备同源 NTP，漂移量级远小于人的操作间隔。真出问题再说 |
| `updatedAt` 毫秒级并列 | 靠 deviceId 定序解决收敛性，但"谁赢"变成 UUID 字典序，反直觉 | 可接受：并列概率本身就极低，且冲突历史让被丢的那一份可见可查 |
| 设备数 = 目录文件数 | 每次同步读全部快照 | 家庭 ≤5 台，几十 KB 级，不做分片/清单索引 |
| SAF 目录不可用（网盘客户端没跑） | 读写报错 | 按 §7.6 静默 + 计数，不影响本地可用性 |

## 12. 落库与文件变更清单（给实现计划用）

| 文件 | 动作 |
|---|---|
| `core/domain/SyncMerge.kt` | 改：`OnTie` 参数（默认 `PREFER_INCOMING`，保住恢复语义与现有测试）、同步路径总序 `wins()`、三桶 `MergeResult` + 派生 `skipped`、`Dropped` |
| `core/domain/Backup.kt` | 改：`toJson(items, deviceId?)`、`metaOf(json)` 读 deviceId |
| `core/domain/SyncEngine.kt` | 新：`syncOnce()` 纯编排，可 JVM 测。依赖两个小接口而不是具体类——`SyncDriver`（list/read/write）与 `SyncStore`（`snapshot(): List<Item>` / `merge(items, onTie)`），`ItemRepository` 实现后者；否则 §9.3 的双目录仿真测就得拖进 Room 跑不了 JVM |
| `core/data/Sync.kt`（新表 + DAO） | 新：`SyncConflict` 实体、`SyncConflictDao`（insert/oldestBeyond/deleteBeyond/count） |
| `core/data/AppDatabase.kt` | 改：version 3、`MIGRATION_2_3`（只 `CREATE TABLE sync_conflicts`）、schema JSON 导出 |
| `core/data/ItemRepository.kt` | 改：`mergeFromSync(items)`（用 `getAllIncludingTombstones` + 记冲突 + 200 条滚动裁剪） |
| `core/sync/SyncDriver.kt` / `LocalFolderDriver.kt` | 新：接口 + SAF DocumentFile 实现（tree uri 持久化） |
| `ui/ItemsViewModel.kt` | 改：`syncNow()`（Mutex 单飞）、`_syncState`、开屏 `LaunchedEffect` 一次 |
| `feature/settings/SettingsScreen.kt` | 改：新增「同步」`EkCard` |
| `feature/sync/ConflictListScreen.kt` + `EkApp.kt` 路由 | 新：冲突历史页 |
| `core/data/AppPrefs.kt` | 改：`syncTreeUri`、`lastSyncAt`（**不存任何凭证**） |
| 测试 | `SyncMergeTest` 扩（顺序无关/幂等/平局）、`BackupTest` 扩（墓碑往返）、`SyncEngineTest` 新（假 driver）、`RoomMigrationTest` 扩（2→3） |

预估：单测从 55 增至 ~75，schema v3 一次，UI 一处卡片 + 一页列表。
