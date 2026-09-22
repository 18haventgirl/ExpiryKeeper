package com.expirykeeper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expirykeeper.App
import com.expirykeeper.data.Item
import com.expirykeeper.data.ItemRepository
import com.expirykeeper.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
}
