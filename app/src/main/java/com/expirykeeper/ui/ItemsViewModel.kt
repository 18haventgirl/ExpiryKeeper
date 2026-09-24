package com.expirykeeper.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expirykeeper.App
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemEvent
import com.expirykeeper.core.data.ItemRepository
import com.expirykeeper.core.data.ItemSort
import com.expirykeeper.core.domain.Backup
import com.expirykeeper.core.domain.Reminder
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.notifications.NotificationHelper
import com.expirykeeper.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** 一次性提示消息：actionLabel 非空时 Snackbar 带动作按钮，Performed 后回调 action */
data class SnackbarMsg(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class ItemsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: ItemRepository = (application as App).container.repository

    private val prefs = (application as App).container.prefs

    private val today: LocalDate get() = LocalDate.now()

    /**
     * 一次性 Snackbar 事件流（EkApp 顶层 Scaffold 消费）；extraBuffer 防无订阅瞬丢。
     * I-2：replay=1 让"删除可撤销"这条最后消息能穿过配置变更/重订阅窗口——
     * deleteWithUndo 的撤销动作闭包随消息一起驻留，UI 重建后新订阅者仍会收到并可点撤销。
     */
    private val _snackbar = MutableSharedFlow<SnackbarMsg>(replay = 1, extraBufferCapacity = 4)
    val snackbar: SharedFlow<SnackbarMsg> = _snackbar.asSharedFlow()

    /**
     * Room 首包是否已到。修 B4：items 的初值是 emptyList，加载中的那一帧与「真没有数据」
     * 长得一样，UI 会先闪一次「还没有物品」。首包到达后置为已完成。
     */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val items: StateFlow<List<Item>> = repo.observeAll()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 今日应提醒的条目（引擎 computeForDate 快照） */
    val reminders: StateFlow<List<Reminder>> = items.map { ReminderEngine.computeForDate(it, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 未来 14 天到期摘要（Hero "两周内" 统计用） */
    val upcoming14: StateFlow<List<Pair<Item, Long>>> = items.map { ReminderEngine.upcoming(it, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 清单搜索关键词：匹配 名称/备注/位置，忽略大小写；空串 = 不过滤 */
    val filterQuery = MutableStateFlow("")

    /** 清单排序方式（仅会话内记忆，不持久化） */
    val sortOrder = MutableStateFlow(ItemSort.EXPIRE_ASC)

    /** items × filterQuery × sortOrder 派生：过滤 + 4 路排序后的可见清单 */
    val visibleItems: StateFlow<List<Item>> = combine(items, filterQuery, sortOrder) { list, q, sort ->
        list.asSequence()
            .filter {
                q.isBlank() ||
                    it.name.contains(q, ignoreCase = true) ||
                    (it.note?.contains(q, ignoreCase = true) ?: false) ||
                    (it.location?.contains(q, ignoreCase = true) ?: false)
            }
            .let { seq ->
                when (sort) {
                    ItemSort.EXPIRE_ASC -> seq.sortedBy { ReminderEngine.effectiveExpireDay(it) ?: Long.MAX_VALUE }
                    ItemSort.NAME -> seq.sortedBy { it.name }
                    ItemSort.CREATED_DESC -> seq.sortedByDescending { it.createdAt }
                    ItemSort.CATEGORY -> seq.sortedBy { it.categoryId }
                }
            }.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(item: Item) = viewModelScope.launch {
        repo.save(item)
        ReminderScheduler.runNow(getApplication())
    }

    /** 详情屏删除：软删 + 撤消退路；撤销走 repo.save（updatedAt 刷新 → 大于墓碑，重新可见） */
    fun deleteWithUndo(id: String) = viewModelScope.launch {
        val original = repo.getById(id) ?: return@launch
        repo.softDelete(id)
        NotificationHelper.cancelItem(getApplication(), id)
        ReminderScheduler.runNow(getApplication())
        _snackbar.emit(
            SnackbarMsg("已删除《${original.name}》", "撤销") {
                viewModelScope.launch {
                    repo.save(original)
                    ReminderScheduler.runNow(getApplication())
                }
            },
        )
    }

    /** 详情浮层数据源：Room 实时流，快速操作后 UI 自动刷新 */
    fun observeItem(id: String): Flow<Item?> = repo.observeById(id)

    /** 该物品最近 30 天事件流水（详情"最近记录"） */
    suspend fun recentEventsFor(itemId: String): List<ItemEvent> =
        repo.recentEvents(30).filter { it.itemId == itemId }

    /** 设置概览：30 天续期(roll)次数 */
    suspend fun rollCount30d(): Int = repo.rollCount30d()

    /** 动态取色开关：写 prefs 后由 UI 触发 activity recreate 生效（读侧 UI 直接取 prefs.dynamicColor） */
    fun setDynamicColor(value: Boolean) {
        prefs.dynamicColor = value
    }

    /** 快速操作·续期：按保质期/周期滚期；无法推导时把原因发到 Snackbar 总线（修 B18，反馈只有一条通道） */
    fun rollForward(id: String) = viewModelScope.launch {
        if (!repo.rollForward(id, today)) {
            _snackbar.emit(SnackbarMsg("这件没有保质期或周期规则，去编辑里补上"))
        }
        NotificationHelper.cancelItem(getApplication(), id)
        ReminderScheduler.runNow(getApplication())
    }

    /** 快速操作·今天不再提醒：标记 handled，次日引擎自动恢复；cancelItem 兜底清掉旧日期变体的通知 */
    fun markHandled(r: Reminder) = viewModelScope.launch {
        repo.markHandled(r.item.id, r.status.name, today)
        NotificationHelper.cancelItem(getApplication(), r.item.id)
        ReminderScheduler.runNow(getApplication())
    }

    /** 快速操作·稍后 3 天：静默今天~+2、第 3 天(+3)恢复提醒；cancelItem 兜底清掉旧日期变体的通知 */
    fun snooze3(r: Reminder) = viewModelScope.launch {
        repo.snooze(r.item.id, 3, today)
        NotificationHelper.cancelItem(getApplication(), r.item.id)
        ReminderScheduler.runNow(getApplication())
    }

    suspend fun getById(id: String): Item? = repo.getById(id)

    /** 导出全部存活条目到 SAF uri；IO 全在 viewModelScope + Dispatchers.IO，回调回到主线程 */
    fun exportBackup(uri: Uri, onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching {
                withContext(Dispatchers.IO) {
                    val all = repo.getAll()
                    val json = Backup.toJson(all)
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IOException("无法写入所选文件")
                    stream.use { it.write(json.toByteArray()) }
                    all.size
                }
            })
        }
    }

    /** 导入：整文件先解析校验（Backup.parse 抛类型化异常），任何垃圾数据都不会触碰到库 */
    fun importBackup(uri: Uri, onDone: (Result<Pair<Int, Int>>) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("无法读取所选文件")
                    val text = stream.bufferedReader().use { it.readText() }
                    val incoming = Backup.parse(text)
                    repo.importMerged(incoming).also { ReminderScheduler.runNow(getApplication()) }
                }
            })
        }
    }
}
