package com.example.offlineplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** שומר את נקודת העצירה של כל קובץ, כדי להמשיך משם בפעם הבאה. */
class PositionStore(context: Context) {
    private val prefs = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    fun get(mediaId: String): Long = prefs.getLong(mediaId, 0L)

    fun save(mediaId: String, positionMs: Long) {
        prefs.edit().putLong(mediaId, positionMs).apply()
    }

    fun clear(mediaId: String) {
        prefs.edit().remove(mediaId).apply()
    }
}

/** רשימות השמעה: שם הרשימה -> רשימת מזהי קבצים. נשמר מקומית כ-JSON. */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun all(): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        val root = runCatching { JSONObject(prefs.getString(KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val names = root.keys()
        while (names.hasNext()) {
            val name = names.next()
            val arr = root.optJSONArray(name) ?: continue
            result[name] = List(arr.length()) { arr.getString(it) }
        }
        return result
    }

    fun create(name: String) {
        val n = name.trim()
        val map = LinkedHashMap(all())
        if (n.isEmpty() || n in map) return
        map[n] = emptyList()
        write(map)
    }

    fun delete(name: String) {
        val map = LinkedHashMap(all())
        map.remove(name)
        write(map)
    }

    fun add(name: String, mediaId: String) {
        val map = LinkedHashMap(all())
        val current = map[name] ?: return
        if (mediaId in current) return
        map[name] = current + mediaId
        write(map)
    }

    fun remove(name: String, mediaId: String) {
        val map = LinkedHashMap(all())
        val current = map[name] ?: return
        map[name] = current - mediaId
        write(map)
    }

    private fun write(map: Map<String, List<String>>) {
        val root = JSONObject()
        map.forEach { (name, ids) -> root.put(name, JSONArray(ids)) }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private companion object {
        const val KEY = "data"
    }
}
