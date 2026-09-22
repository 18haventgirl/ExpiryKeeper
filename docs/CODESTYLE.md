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
