# 到期管家（暂名）—— 家庭物品保质期/到期提醒 App 设计规格

日期：2026-09-22　状态：已获用户批准，开发中
进度记录见 `docs/PROGRESS.md`。

## 1. 一句话定位

本机优先的"万物到期管家"：食物、药品、化妆品、数码保修、证件、会员续费一处管理，到期前自动提醒全家。纯自用工具，不做商业化。

## 2. 决策记录（为什么是这个产品/这个架构）

| 决策 | 结论 | 原因 |
|---|---|---|
| 原 token 统计项目 | 放弃 | 开源已高度成熟（codeburn 11k★、tokscale、aiusage 等），无做轮子的必要 |
| 形态 | Android 原生应用 | 用户是安卓开发者；端侧体验是唯一护城河 |
| 用户形态 | 家庭共享（妈妈添加、全家受益） | 品类杀手体验与留存来源 |
| 录入 | MVP = 手动 + 条形码，OCR 二期 | 控制首版范围；条码命中率有限需手动兜底 |
| 品类 | 全家桶（分步实现，数据模型统一） | 提醒语义收敛为 3 种（见 §4） |
| 发布 | 纯自用，不上架、无账号合规 | 免 ICP/推送通道等全部包袱 |
| 同步 | 本地优先 + SyncDriver 接口：先 LocalFolder 验证，后 WebDAV（坚果云免费额度/NAS） | 零服务器零月费；国内可用 |
| 提醒 | 全本地生成（WorkManager 日扫 + 精确闹钟） | 不需要推送服务器，砍掉后端的关键 |
| 技术栈 | Kotlin + Jetpack Compose + Room + WorkManager，minSdk 29 | 标准现代安卓栈 |

## 3. 架构分层

```
ui/         Compose 界面：今日 / 清单 / 添加(表单+扫码) / 详情 / 设置
reminders/  纯函数规则引擎：items 快照 + now → 应发通知列表（无 Android 依赖，JVM 单测覆盖）
            + WorkManager 每日兜底扫描 + 当日精确闹钟（SCHEDULE_EXACT_ALARM，被拒则降级普通通知）
data/       Room DAO + Repository；所有写操作在同一事务内落 change_log
sync/       SyncDriver 接口：pull 远端变更 → 按 updatedAt LWW 合并（含墓碑）→ push 本地未 ack 变更
            M1 不接驱动；M3 实现 LocalFolderDriver（同机双目录模拟两设备跑冲突测试）；M4 WebDavDriver
            配对 = 二维码导入同步配置（URL+凭据）
```

## 4. 数据模型（Room）

```
items
├─ id: UUID(String) / name / barcode? / photoUri?
├─ categoryId → categories（预置6类：食材/药品保健/化妆品/数码/证件/会员订阅，可扩展）
├─ location?（冰箱冷藏/抽屉…）
├─ qty: Double? + unit?（"还剩2盒"）
├─ 提醒语义 reminderKind 三选一（品类模板预设，可改）：
│   EXPIRY     expireAt: LocalDate? → 提前 offsetsDays 多天提醒（模板如 [7,3,0]）
│   CONSUMABLE lowStockThreshold → qty 低于阈值提醒
│   RECURRING  nextDueAt + recurrence(月/年/N天) → 到期前提醒并可自动滚期
├─ note? / createdAt / updatedAt(ms) / lastModifiedBy(deviceId) / deletedAt?(墓碑)

change_log（增量同步单元）
└─ seq / itemId / snapshot(updatedAt后的整条记录JSON) / op(upsert|delete) / ackedLocally/ackedRemotely

settings：deviceId(实例级随机)、通知偏好、同步配置
```

品类模板（category_templates）：每类带默认 reminderKind、默认保质期常识（"开封牛奶3天"）、默认提醒偏移。添加流程选品类 → 规则自动填好。

## 5. 错误处理与边界

- 通知/闹钟权限被拒：设置页引导 + 降级为泛通知，不阻塞核心流。
- 条码查询离线/无结果：仅自动填条码号，其余手动，绝不卡流程。
- 同步冲突：LWW by updatedAt；删除=墓碑，合并后墓碑过期清理（>30天）。
- 设备时钟偏移：同步时记录本机与服务端时间差用于修正 updatedAt 比较。
- 数据库迁移：Room schema version + 迁移测试。

## 6. 测试策略

- 规则引擎：纯函数 JVM 单测（边界：当天、多档偏移、周期滚期）。
- Repository/change_log：Room 内存数据库仪器测试。
- 同步合并：LocalFolderDriver 双设备仿真测试（并发编辑、删除复活、离线一周回归）。
- UI 手测清单见 PROGRESS 各里程碑验收项。

## 7. 里程碑

- **M1 单机可用**：项目脚手架、数据层、添加/清单/今日三屏、EXPIRY 提醒本地通知。自用跑一周。
- **M2 品类完备**：CONSUMABLE/RECURRING 语义、6 品类模板与保质期常识库、条码扫描（MLKit）、多档提醒。
- **M3 同步协议**：change_log + SyncDriver + LocalFolderDriver 双目录仿真 + 冲突测试。
- **M4 家庭共享**：WebDavDriver、二维码配对、成员标识（谁加的）、全家通知。
- **M5 打磨**：照片、统计（本月丢掉多少过期食物）、桌面小组件/图标角标。

## 8. 待定

- 产品正式名称未定（暂名"到期管家"，工程代号 expiry-keeper）。
- 中文条码公开 API 选型（M2 时验证，失败则降级纯手动填名）。

## 9. v2 品质升级要求（2026-09-22 用户验收 M1 后提出，硬性红线）

1. **稳健**：零致命 bug、零低级错误、不丢数据、不死机；必须有数据导出/导入备份出路。
2. **美观**：对标 Google/Apple 系统级 App 设计语言——Material 3 Expressive（大圆角形状档位、expressive typography、动态取色 dynamic color、克制动效、暗色完整适配）。
3. **内容丰富实用**：品类/任务用 emoji 图标做视觉区分；物品级自定义 emoji；增加搜索、排序、统计概览、详情历史、快速操作（吃掉/续期/处理掉）。
4. **工程规范**：工作区干净（git 纳管、reference/ 隔离）、feature/core 分层包结构、单文件单职责、统一代码风格文档。

实施计划：`docs/superpowers/plans/2026-09-22-expiry-keeper-v2-quality.md`。
