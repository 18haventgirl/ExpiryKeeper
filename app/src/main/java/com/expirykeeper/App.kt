package com.expirykeeper

import android.app.Application
import androidx.room.Room
import com.expirykeeper.core.data.AppDatabase
import com.expirykeeper.core.data.ItemRepository
import com.expirykeeper.notifications.ReminderScheduler

class AppContainer(
    val repository: ItemRepository,
    val deviceId: String,
)

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "expiry-keeper.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        var deviceId = prefs.getString("deviceId", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", deviceId).apply()
        }
        container = AppContainer(ItemRepository(db.itemDao(), db.changeLogDao(), db.eventDao(), deviceId), deviceId)
        ReminderScheduler.schedule(this)
    }
}
