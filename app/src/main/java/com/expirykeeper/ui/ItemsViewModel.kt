package com.expirykeeper.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expirykeeper.App
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemRepository
import com.expirykeeper.core.data.ItemSort
import com.expirykeeper.core.domain.Backup
import com.expirykeeper.core.domain.Reminder
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.notifications.NotificationHelper
import com.expirykeeper.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class ItemsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: ItemRepository = (application as App).container.repository

    private val today: LocalDate get() = LocalDate.now()

    val items: StateFlow<List<Item>> = repo.observeAll()
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

    fun delete(id: String) = viewModelScope.launch {
        repo.softDelete(id)
        ReminderScheduler.runNow(getApplication())
    }

    fun consumeOne(id: String) = viewModelScope.launch { repo.consumeOne(id) }

    /** 快速操作·续期：按保质期/周期滚期；无法推导只 Toast 提示去编辑（Task 11 前有 snackbar 再升级） */
    fun rollForward(id: String) = viewModelScope.launch {
        if (!repo.rollForward(id, today)) {
            Toast.makeText(getApplication(), "这件没有保质期或周期规则，去编辑里补上", Toast.LENGTH_SHORT).show()
        }
        NotificationHelper.cancelItem(getApplication(), id)
        ReminderScheduler.runNow(getApplication())
    }

    /** 快速操作·今天不再提醒：标记 handled，次日引擎自动恢复 */
    fun markHandled(r: Reminder) = viewModelScope.launch {
        repo.markHandled(r.item.id, r.status.name, today)
        NotificationHelper.cancel(getApplication(), r.notificationId)
        ReminderScheduler.runNow(getApplication())
    }

    /** 快速操作·稍后 3 天：snooze 截止日写 today+3，引擎期间静默 */
    fun snooze3(r: Reminder) = viewModelScope.launch {
        repo.snooze(r.item.id, 3, today)
        NotificationHelper.cancel(getApplication(), r.notificationId)
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
