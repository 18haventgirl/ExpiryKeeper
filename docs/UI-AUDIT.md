# UI / 交互审计（2026-09-24）

**方法**：三路并行取证 —— ① 设计系统与五屏代码审查；② 模拟器逐屏操作 + `uiautomator` bounds 量化 + PNG 取色/WCAG 对比度 + `gfxinfo` 帧统计；③ GitHub 开源项目与 M3 规范调研。三路互相独立，**两路同时撞上的问题记为 P0（最高置信）**。

**度量口径**：emulator-5554 · Pixel 9 AVD · Android 17 / API 37 · 1080×2424 · **420dpi → density 2.625（px ÷ 2.625 = dp）**。
统一栅格基线：页面横向 16dp → 左缘 42px；卡片内衬 16dp → 内容 84px；胶囊右缘 996px。

**取证产物**：`.debug-ui/audit/`（已 gitignore，不入库）。内含 40+ 张截图与同名 xml，以及三个可复用 node 工具：`dump.js`（解析 bounds/间距/左缘/点击）、`png.js`（零依赖 PNG 解码 + 行列取色）、`cr.js`（WCAG 对比度）。

---

## A. P0 —— 两路独立撞上的问题

| ID | 问题 | 证据 | 性质 |
|---|---|---|---|
| A1 | 详情浮层背后整页被清空 | `EkApp.kt:98-106` `detail/{id}` 为普通路由，`Box(fillMaxSize())` 内只放 Sheet，来路屏被踢出组合；像素：遮罩区仅 1 色（`#aaa8ac`）vs 同区域清单页 82 色，对照表情浮层 194 色 | 结构 |
| A2 | 添加页/设置页保留底栏，主 CTA 首屏在屏外 | 未滚动时保存按钮被裁至 26px 高；底栏 210px 全程在场；设置页概览卡被腰斩（行高 38px vs 正常 53px） | 结构 |
| A3 | 空表单首帧满屏红 | `AddEditScreen.kt:121,124` 无条件由 `isBlank()` 推导错误 → 零输入即渲染「请填写物品名称」(y=1343)、「请选择到期日期」(y=2009) + 灰按钮 | 正确性 |
| A4 | 逾期一律显示「0」，与「今天到期」完全同形 | `ReminderEngine.kt:63` `daysLeft.coerceAtLeast(0)` + `Cards.kt:103` `coerceIn(0,14)/14f` → 空弧 + 「0」；`overdueDays`（:25）全 UI 无人读；实测逾期 3 天与逾期 1 天长得一样 | **正确性** |
| A5 | 清单尾部槽位混用两种语义 | 6 条中 3 条尾部是光秃秃「—」(29×53px，像素占比 2.8%)；有状态的条目永远看不到数量 | 正确性 |
| A6 | Hero 计数与今日分组口径不一致 | `ReminderEngine.kt:60` `DUE_SOON` 要求 `daysLeft` **恰好等于** offset，计数器用区间 → 面霜/视频会员被计数却永不上榜，数字与列表自相矛盾 | **正确性** |

> A4、A6 属红线①（引擎口径不一致导致信息静默丢失），不是美化问题，最先修。

## B. P1 —— 质感与规范

- **B1 层级不分**：页面 `#faf8fe` / 卡片 `#ededf6` → CR **1.104**（暗色 1.098），导航栏与卡片同色，全靠 2dp 阴影撑
- **B2 计数误用 M3 `Badge`**：`Headers.kt:32` Badge 容器色为 `error` → 分组头挂红底数字，一屏四个红点制造虚假紧急感，稀释真逾期的红
- **B3 零动效语言**：清单行无 `Modifier.animateItem()`（今日页有）；`EkApp.kt:83` NavHost 未设 enter/exit；全项目 0 处 `motionScheme` / `animateContentSize` / `sharedBounds`
- **B4 首帧空态闪烁**：`ItemsViewModel.kt:53-62` `stateIn(initialValue = emptyList())` → Room 首包前渲染「还没有物品」；`AddEditScreen.kt:112` `if (!loaded) return` 白闪一帧
- **B5 禁用态文字不可读**：`#b1b1b9` on `#e6e4ea` → CR **1.69**
- **B6 字号撞车**：同屏四个 40sp `displaySmall`；Hero 三统计数字同权重同色无主次；卡片标题三套（titleLarge / titleMedium+Bold / titleLarge+Bold）
- **B7 搜索框缺前后缀**：`ListScreen.kt:92` 无 `leadingIcon`/`trailingIcon` → 没有放大镜、打字后无一键清除；旧查询跨 tab、跨主题切换静默存活
- **B8 间距野值**：10/6/4/14/18/26dp 不在 4-8-12-16-24-32 上；`TodayScreen.kt:102`、`AddEditScreen.kt:296`、`SettingsScreen.kt:268` 三处 `Spacer(Modifier.width(1.dp).padding(bottom=…))`（应为 `height`）；今日页缺 `contentPadding(bottom=96.dp)` → 末卡被 FAB 压住；`DetailSheet.kt:75` 用 24dp 横边距与全局 16dp 不一致
- **B9 emoji 当功能图标 + 触控不足**：`SettingsScreen.kt:296` ✅/⚠️ 表达权限状态、`DetailSheet.kt:186`「✏️ 编辑」、`EmojiPicker.kt:41`「✓ 跟随品类」；`AssistChip` 32dp、emoji chip 36dp、折叠行 <48dp；`Cards.kt:176` `maxLines=1` 未配 `overflow`
- **B10「🍽 续期」渲染成灰白线稿**：U+1F37D 默认文本呈现，同排 😴/✅ 满彩，一眼像缺字
- **B11 分隔符撞车**：品类名自带「·」与分隔符「 · 」→「化妆品·个护 · 梳妆台第二层」，一个符号两种语义
- **B12 空态顶吊**：搜索无结果时内容吊在搜索框下，下方约 438dp 全空；且「0 件」用过滤后条数，搜索时总件数消失
- **B13 无 edge-to-edge 策略**：`MainActivity` 无 `enableEdgeToEdge()`，targetSdk 37 平台强制边到边 → 状态栏图标对比度无人管（与已修的 SearchBar inset 双算是同一病根：全 App 没有 inset 策略）
- **B14 大字号未适配**：`Type.kt` 无 fontScale 处理，Hero 三列 40sp 在 200% 字号下溢出成两行
- **B15 返回箭头与标题不同列**：箭头 x=85px(32dp) vs 标题 x=42px(16dp)，源于 `BigHeader.kt:74` 的 `start=4.dp`；页头顶距三屏 24dp、添加页 32dp
- **B16 长按与单击行为相同**：`ListScreen.kt` `onClick`/`onLongClick` 都调 `onDetail`，白给的手势
- **B17 按钮失衡**：保存 812px vs 取消 152px（5.3 倍），取消内边距 14.9dp < M3 24dp
- **B18 反馈双轨**：备份结果写卡内 `Text`（`SettingsScreen.kt:233`）、`rollForward` 失败用 `Toast`（`ItemsViewModel.kt:128`）→ 应统一走 `vm.snackbar`

## C. 设计系统缺口（红线④）

- `EkSpacing`：rhythm 散落，野值 10/14/20/6/5/2 混用；`ItemCard.kt:159` 竖衬 14dp 与「内衬 16dp」不一致
- `EkCard(title, level)`：`ItemCard`/`FormCard`/`SettingsCard`/`HeroCard` 四处重复 `surfaceContainer` + `shapes.large` + elevation（一份 2dp 一份 1dp）；M3 里「filled 容器 + 阴影」是双抬升信号，应二选一
- `Pill(text, tone)`：`StatusPill` 与 `DetailSheet.NeutralPill` 同形状两份实现
- `KeyValueRow`：`DetailSheet.KvRow(96.dp)` 与 `Settings.KvLine(120.dp)` 两份
- `CountPill` / `StatusGlyph`：取代 B2 的 Badge 与 B9 的 ✅⚠️
- `FormWidgets.kt`（FormCard/ErrorLine/CategoryStrip/DateField/EmojiIconButton）实际位于 `feature/addedit`，通用车间件应上收 `core/ui/designsystem`
- `Shapes` 阶梯几乎未用：chips 硬写 `RoundedCornerShape(50)`、品类 chip 硬写 `16.dp`
- `EmojiSize`/`IconSize` token 缺失：18/22/28/44/64sp 五处硬编码
- 日期格式不统一：`FormWidgets.kt:121`、`RuleSections.kt:86` 用 `yyyy-MM-dd`，首页用「M月d日」，跨年无年份
- UI 直写 `vm.filterQuery.value=` / `vm.sortOrder.value=`，应走 VM 方法
- `SettingsScreen.kt:169-268` 整段多缩进 4 格；`PCategoryChip` 命名残留

## D. 已确认没问题（不要重复劳动）

左缘栅格统一（16dp→42px、卡内 84px、三处一致，胶囊右缘 996px）；**正文对比度全过 AA**（明暗 5.18–15.40，状态胶囊 逾期 12.77/7.17、库存低 6.12/6.08）；按压反馈真实（卡片缩放实测 0.9705 + 整卡 state layer 染色）；**滚动性能无问题**（231 帧、jank 2.60%、99 分位 34ms、Missed Vsync 0）；搜索逻辑正确稳定（L/LE/LED 恒 1 件、结果固定 y=674、无跳变），IME 搜索键正常收起键盘；返回手势矩阵合理（键盘→浮层→路由逐级出栈，浮层可下拉关闭）；tab 切换有交叉淡入；NavigationBar 80dp + 24dp 手势区、FAB 56dp/16dp 边距，今日与清单两屏均无遮挡。

## E. 外部参考（star 数经 api.github.com 实查，2026-09-24）

| 项目 | 抄什么 |
|---|---|
| [android/nowinandroid](https://github.com/android/nowinandroid) 21.8k★ | `theme/Background.kt` 渐变底 + `LocalBackgroundTheme(tonalElevation)`，**开动态取色时自动降级为平面**；`core/designsystem/src/test/screenshots/` 用 light/dark × dynamic/static 四组截图回归固化审美 |
| [android/compose-samples](https://github.com/android/compose-samples) 23.5k★ | Jetsnack `components/Snacks.kt` 的 `Modifier.sharedBounds(...)` + `OverlayClip(RoundedCornerShape)`（清单→详情转场官方写法）；JetLagged `HomeScreenCards.kt` hero「12sp 标签 / 36sp 数字 / 12sp 单位」三行结构 |
| [shub39/Grit](https://github.com/shub39/Grit) 1.2k★ | `task/ui/component/TaskCard.kt`：状态色 `animateColorAsState(..., motionScheme.fastEffectsSpec())`，容器 `secondaryContainer ↔ surfaceContainerHighest` **纯 tonal 零阴影**；`components/Empty.kt` 空态 = 64dp 线性图标 + 一行居中提示 |
| [JunkFood02/Seal](https://github.com/JunkFood02/Seal) 29.2k★ | `ModalBottomSheetM3.kt`（`skipPartiallyExpanded` + `velocityThreshold=56.dp` + 水平/底部 28dp）；`PreferenceItems.kt` 禁用态用 `onSurfaceVariant.applyOpacity()` 而非换灰字色 |
| [waseefakhtar/dose-android](https://github.com/waseefakhtar/dose-android) 0.6k★ | `util/DurationFormatter.kt` 用 `pluralStringResource` 出「剩 1 天 / 3 天」；今日卡 `primaryContainer` 底 + `tertiary` 字且卡片可点 |
| [mhss1/MyBrain](https://github.com/mhss1/MyBrain) 2.2k★ | `theme/Type.kt` 的 `getTypography(font, fontSizeScale)` —— 全局字号缩放（治 B14） |

**Skill 生态**：
- [anthropics/skills → frontend-design/SKILL.md](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md)（177.8k★，纯文本零依赖）：AI 默认审美黑名单 + 「一处下重注、其余全安静」「空屏是行动的邀请」「错误要说清怎么修」→ **可直接当审美红线**
- [JusDots-Devs/m3e-skill](https://github.com/JusDots-Devs/m3e-skill)（1★，2026-09-07）：唯一命中 M3 Expressive + Compose（针对 material3 1.4–1.5），含 ANTI_PATTERNS/MOTION/COMPOSE_IMPLEMENTATION；**star 太少，API 细节需逐条自行核对**
- [ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)（130k★）：其 `jetpack-compose.csv` 52 行几乎全是状态提升/recomposition 工程规范，审美部分面向 Web，**对 Android 帮助有限**
- Qoder 现有 skill 无 Android UI 美化类

**M3 规范性价比清单**：① 排版全量对齐 15 role + editorial treatments；② shape ladder 一致、一屏不超两档圆角、shape-morph 只用于交互；③ tonal surfaces 取代边框阴影、禁用态走 state layer；④ `MaterialTheme.motionScheme.fastEffectsSpec()/fastSpatialSpec()` + `animateColorAsState`/`animateContentSize`；⑤ edge-to-edge + SystemBarStyle + predictive back。

## F. 与第一方水准的结构性差距

1. **零 motion 语言**（B3）—— 「第一方感」最大缺口
2. **排版不成 scale**（B6/C）—— `Type.kt` 只覆 2 档，层级靠界面层 `copy(fontWeight=)` 硬补
3. **层级靠阴影不靠 tonal surface**（B1）
4. **系统栏/inset 无策略**（B13 + 已修的 SearchBar 双算）
5. **「一眼可得」不足**（A4/A5/B12）—— 无相对时间与复数工具，统计未做成内容
6. **无视觉回归**（对照 NIA 四组截图测试）

---

## 施工批次

### 第 1 批 · 正确性（A4 A6 A3 B4）✅ 已完成

**裁决记录**

- **A4**：新增纯函数 `ringSpec(status, daysLeft, overdueDays, offsets) → RingSpec(text, fraction)`，`DueRing` 退化为只渲染规格、配色取自 `StatusTone`。规则：逾期=满环+逾期天数；今天=空环+「今」；其余=按窗口递减。窗口取 `max(14, 最远提前量)`，否则药品类 60/30 天偏移的物品永远满环。
- **A6**：**通知仍按精确 offset 触发**（引擎 `DUE_SOON` 语义不变，不打扰原则），今日屏的「即将到期」组改由 `ReminderEngine.soonSection(upcoming14, 1..14)` 提供 —— 数字与列表同源，结构上不可能再各说各话。hero 三个数分别等于：待处理=三组行数之和、两周内=即将到期行数、全部=清单项数。
- **A3**：规则校验从 `AddEditScreen` 抽为 `ExpiryForm.ruleError(RuleState)`（顺带补上此前零覆盖的跨字段规则测试）。**保存按钮不再置灰**：原 I-4 用 disabled 表达无效态，改为点一次揭示全部缺失错误。这同时消掉 B5（禁用文字 CR=1.69 不可读）。不写入任何无效数据。
- **B4**：`ItemsViewModel` 加 `_loaded`/`isLoading`（`onEach` 挂在既有 `observeAll` 上，不二次收集），今日/清单空态改为 `isEmpty && !loading`；添加页 `if (!loaded) return` 换成居中 `CircularProgressIndicator`。

**验证**：单测 32 → **45 全绿**（引擎 +9、表单 +4，其余为既有用例）；lint 0 error。设备实测（epoch-day 20719 = 2026-09-23 GMT，注意设备 TZ 是 GMT 与宿主 +8 不同日）：感冒灵逾期3→「3」满弧、牛奶逾期1→「1」满弧（像素 8/8 采样确认）、视频会员→「2」约 51° 弧、面霜→「6」；待处理 5 = 紧急2+即将到期2+需要关注1，两周内 2 = 即将到期 2；新建表单首帧错误文本 **0 条**（修复前 2 条），点保存后出现 2 条且未写入（items 仍 6 条）。

**过程中新撞到的证据**：`面霜` 的到期环被 FAB 压住（弧采样 0/8 全非环色，因为量到 FAB 像素）—— 直接坐实 B8「今日页缺 `contentPadding(bottom=96.dp)`」；另外新建页的保存键首屏不可见（需滚动到 y=1967 才出现）—— 坐实 A2。

- [x] A4 逾期天数可见：OVERDUE 显示 `overdueDays` + error 色弧 + 「逾 N 天」；分母不再锁 14 天
- [x] A6 `DUE_SOON` 组改区间口径，与 hero 同源
- [x] A3 表单校验加 revealed 门槛（保存点击后揭示）
- [x] B4 暴露 `isLoading`，加载中不再用空集合冒充空态；编辑页去白闪

### 第 2 批 · 结构性 P0（A1 A2 A5 B13）✅ 已完成

**裁决记录**

- **A1 走过一条弯路**：先用 navigation-compose 的 `dialog("detail/{id}")` 路由（2.10.1 确有此 API，`NavHost` 内部自动挂 `DialogHost`）。清单确实透出来了，但实测透过率 **0.27 = 0.32(sheet scrim) × 0.60(dialog 窗口 dim)** —— 双重压暗，背景糊成一片。而 Compose UI **1.12.1 的 `DialogProperties` 根本没有 `dimAmount`**（只有 dismissOnBackPress/dismissOnClickOutside/usePlatformDefaultWidth/securePolicy/decorFitsSystemWindows/windowTitle/windowType/windowToken），窗口那层 dim 关不掉。最终改为**在 `EkApp` 根 Box 里、Scaffold 之上渲染 `DetailSheet`**：只剩 `ModalBottomSheet` 自己那层 32% scrim（正是 M3 规范值），底栏连同内容一起被压暗，来路屏永不离开组合。`detail/{id}` 路由与 `navArgument`/`NavType` 相关分支随之删除。
- **A2**：`chromeVisible = currentRoute == "today" || "list"` 统一驱动 bottomBar 与 FAB（顺带取代原来那串三重否定）。
- **A5**：尾部槽位只表达「到期」一件事 —— 有提醒→状态胶囊；无提醒但有到期日→`daysCaption` 相对天数；两者皆无→留空。数量+单位移入副标题（`ItemCard` 新增可选 `detail` 覆盖参数）。**过程中发现 `effectiveExpireDay` 不覆盖 RECURRING**，导致订阅类物品尾部空着：抽出 `ReminderEngine.dueDayOf(item)` 按类型取日子，`upcoming()` 与清单共用（消掉一份重复、补一个测试）。
- **B13**：`MainActivity` 显式 `enableEdgeToEdge()`（targetSdk 37 平台本就强制，显式调用才能由 SystemBarStyle 接管图标配色）。
- 顺手：`ItemCard` 标题/副标题补 `overflow = TextOverflow.Ellipsis`（B9 的子项，防半字截断）。

**验证**：47 单测全绿、lint 0 error。设备实测：浮层遮罩区透过率 0.68（单层 32%）、清单内容 **309 色**透出（修复前 1 色）；一次 BACK 即关闭浮层（`最近记录` 计数 1→0）且 items 仍 6 条无误删；添加页底栏消失、保存键完整落在 y=2307-2360（修复前被裁到只剩 26px）、首帧无红字且按钮不再是不可读的灰态；面霜「剩 6 天」、LED灯泡「剩 21 天」、视频会员「剩 2 天」、猫粮「1.2 kg」回到副标题。

- [x] A1 详情浮层改根层覆盖渲染（含 `dialog()` 方案的否决记录）
- [x] A2 add/settings 隐藏 bottomBar 与 FAB
- [x] A5 尾部语义分离 + `dueDayOf` 抽取
- [x] B13 edge-to-edge 显式化

### 第 3 批 · 质感（B1 B2 B3 B5 B6 B7 B8 B9 B10 B11 B12 B15 B16 B17 B18）
- [ ] B1 tonal surface 层级（页面降一档或卡片改描边）
- [ ] B2 `CountPill` 取代 Badge；B9 emoji→`Icon`
- [ ] B3 `animateItem()` + NavHost 转场 + `motionScheme`
- [ ] B6 `Type.kt` 补全 scale，Hero 数字独立层级
- [ ] B5/B7/B8/B10/B11/B12/B15/B16/B17/B18 逐项

### 第 4 批 · 资产化（C + D 差距 6）
- [ ] `EkSpacing`/`EkCard`/`Pill`/`KeyValueRow` token 与组件收敛
- [ ] `FormWidgets.kt` 上收 designsystem；日期格式统一；VM 方法替代直写
- [ ] B14 fontScale 适配
- [ ] 引入 frontend-design 审美红线做 checklist；评估截图回归
