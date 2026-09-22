package com.expirykeeper.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expirykeeper.App
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemRepository
import com.expirykeeper.core.domain.Backup
import com.expirykeeper.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ItemsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: ItemRepository = (application as App).container.repository

    val items: StateFlow<List<Item>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(item: Item) = viewModelScope.launch {
        repo.save(item)
        ReminderScheduler.runNow(getApplication())
    }

    fun delete(id: String) = viewModelScope.launch {
        repo.softDelete(id)
        ReminderScheduler.runNow(getApplication())
    }

    fun consumeOne(id: String) = viewModelScope.launch { repo.consumeOne(id) }

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
