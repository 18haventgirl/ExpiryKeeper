# 进度记录（活文档，随开发增删改）

规格：`docs/superpowers/specs/2026-09-22-expiry-keeper-design.md`

## 当前状态

- [进行中] M1 单机可用 —— 全部代码已写，构建迭代中（AGP 9 内置 Kotlin 适配 + 国内镜像调通）
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
- [ ] T11 详情+设置　[ ] T12 通知 v2　[ ] T13 全面体检

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
