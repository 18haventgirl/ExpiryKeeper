package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupFormatException(msg: String) : Exception(msg)

/** 备份信封 {formatVersion, exportedAt, count, items:[...]}；运行时用 Android 内置 org.json，JVM 测试补 org.json:json */
object Backup {
    fun toJson(items: List<Item>): String {
        items.forEach { if (it.updatedAt < 0) throw BackupFormatException("非法时间戳 ${it.id}") }
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("categoryId", it.categoryId)
                put("location", it.location ?: JSONObject.NULL); put("note", it.note ?: JSONObject.NULL)
                put("barcode", it.barcode ?: JSONObject.NULL); put("emoji", it.emoji ?: JSONObject.NULL)
                put("reminderKind", it.reminderKind.name)
                put("expireAtEpochDay", it.expireAtEpochDay ?: JSONObject.NULL)
                put("reminderOffsetsDays", it.reminderOffsetsDays.joinToString(","))
                put("quantity", it.quantity ?: JSONObject.NULL); put("unit", it.unit ?: JSONObject.NULL)
                put("lowStockThreshold", it.lowStockThreshold ?: JSONObject.NULL)
                put("nextDueAtEpochDay", it.nextDueAtEpochDay ?: JSONObject.NULL)
                put("recurrenceDays", it.recurrenceDays ?: JSONObject.NULL)
                put("openedAtEpochDay", it.openedAtEpochDay ?: JSONObject.NULL)
                put("shelfLifeDays", it.shelfLifeDays ?: JSONObject.NULL)
                put("handledAtEpochDay", it.handledAtEpochDay ?: JSONObject.NULL)
                put("handledStatus", it.handledStatus ?: JSONObject.NULL)
                put("snoozedUntilEpochDay", it.snoozedUntilEpochDay ?: JSONObject.NULL)
                put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                put("lastModifiedBy", it.lastModifiedBy ?: JSONObject.NULL)
                put("deletedAt", it.deletedAt ?: JSONObject.NULL)
            })
        }
        return JSONObject().put("formatVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("count", items.size).put("items", arr).toString(2)
    }

    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else optLong(k)
    private fun JSONObject.optIntOrNull(k: String) = if (isNull(k)) null else optInt(k)
    private fun JSONObject.optDoubleOrNull(k: String) = if (isNull(k)) null else optDouble(k)
    private fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else optString(k)

    /** 全量解析成功才返回；任何结构性垃圾抛 BackupFormatException，调用方据此实现"整文件先校验后写入" */
    fun parse(json: String): List<Item> {
        val root = try { JSONObject(json) } catch (e: JSONException) { throw BackupFormatException("不是有效的备份 JSON") }
        if (root.optInt("formatVersion") != 1) throw BackupFormatException("不支持的备份版本")
        val arr = try { root.getJSONArray("items") } catch (e: JSONException) { throw BackupFormatException("缺少 items 数组") }
        return try {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(
                    id = o.getString("id"), name = o.getString("name"), categoryId = o.getString("categoryId"),
                    location = o.optStringOrNull("location"), note = o.optStringOrNull("note"),
                    barcode = o.optStringOrNull("barcode"), emoji = o.optStringOrNull("emoji"),
                    reminderKind = runCatching { ReminderKind.valueOf(o.getString("reminderKind")) }.getOrDefault(ReminderKind.EXPIRY),
                    expireAtEpochDay = o.optLongOrNull("expireAtEpochDay"),
                    reminderOffsetsDays = (o.optStringOrNull("reminderOffsetsDays") ?: "3,0").split(",").mapNotNull { it.trim().toIntOrNull() },
                    quantity = o.optDoubleOrNull("quantity"), unit = o.optStringOrNull("unit"),
                    lowStockThreshold = o.optDoubleOrNull("lowStockThreshold"),
                    nextDueAtEpochDay = o.optLongOrNull("nextDueAtEpochDay"), recurrenceDays = o.optIntOrNull("recurrenceDays"),
                    openedAtEpochDay = o.optLongOrNull("openedAtEpochDay"), shelfLifeDays = o.optIntOrNull("shelfLifeDays"),
                    handledAtEpochDay = o.optLongOrNull("handledAtEpochDay"), handledStatus = o.optStringOrNull("handledStatus"),
                    snoozedUntilEpochDay = o.optLongOrNull("snoozedUntilEpochDay"),
                    createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
                    lastModifiedBy = o.optStringOrNull("lastModifiedBy"), deletedAt = o.optLongOrNull("deletedAt"),
                )
            }
        } catch (e: JSONException) {
            throw BackupFormatException("备份条目缺少必填字段")
        }
    }
}
