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

    /**
     * 严格选填数值读取（C2）：缺失/JSON null → null；非数字垃圾不再被 optLong 静默折成 0；
     * 负值一律拒绝——时间戳与 epoch-day 在本域内没有合法负值（epoch-day 负数 = 1970 年前）。
     */
    private fun JSONObject.optLongOrNull(k: String): Long? {
        val raw = opt(k)
        if (raw == null || raw === JSONObject.NULL) return null
        val v = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
            else -> null
        } ?: throw BackupFormatException("字段 $k 不是合法数字")
        if (v < 0) throw BackupFormatException("字段 $k 不能为负数")
        return v
    }

    /** 必填时间戳（createdAt/updatedAt）：缺失或非法同样整单拒绝，绝不静默取 0 */
    private fun JSONObject.reqLong(k: String): Long =
        optLongOrNull(k) ?: throw BackupFormatException("缺少必填字段 $k")

    private fun JSONObject.optIntOrNull(k: String) = if (isNull(k)) null else optInt(k)
    private fun JSONObject.optDoubleOrNull(k: String) = if (isNull(k)) null else optDouble(k)
    private fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else optString(k)

    /** 未知 reminderKind（拼写错误/外来版本）拒绝而非回退 EXPIRY */
    private fun JSONObject.reminderKindField(k: String): ReminderKind =
        try {
            ReminderKind.valueOf(getString(k))
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("字段 $k 不是已知提醒类型")
        }

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
                    reminderKind = o.reminderKindField("reminderKind"),
                    expireAtEpochDay = o.optLongOrNull("expireAtEpochDay"),
                    reminderOffsetsDays = (o.optStringOrNull("reminderOffsetsDays") ?: "3,0").split(",").mapNotNull { it.trim().toIntOrNull() },
                    quantity = o.optDoubleOrNull("quantity"), unit = o.optStringOrNull("unit"),
                    lowStockThreshold = o.optDoubleOrNull("lowStockThreshold"),
                    nextDueAtEpochDay = o.optLongOrNull("nextDueAtEpochDay"), recurrenceDays = o.optIntOrNull("recurrenceDays"),
                    openedAtEpochDay = o.optLongOrNull("openedAtEpochDay"), shelfLifeDays = o.optIntOrNull("shelfLifeDays"),
                    handledAtEpochDay = o.optLongOrNull("handledAtEpochDay"), handledStatus = o.optStringOrNull("handledStatus"),
                    snoozedUntilEpochDay = o.optLongOrNull("snoozedUntilEpochDay"),
                    createdAt = o.reqLong("createdAt"), updatedAt = o.reqLong("updatedAt"),
                    lastModifiedBy = o.optStringOrNull("lastModifiedBy"), deletedAt = o.optLongOrNull("deletedAt"),
                )
            }
        } catch (e: JSONException) {
            throw BackupFormatException("备份条目缺少必填字段")
        }
    }
}
