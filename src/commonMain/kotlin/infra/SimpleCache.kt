package infra

import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

class SimpleCache {
    @PublishedApi
    internal val store = mutableMapOf<String, String>()

    fun get(key: String): String? = store[key]

    fun set(key: String, value: String) {
        store[key] = value
    }

    fun delete(key: String) {
        store.remove(key)
    }

    inline fun <reified T> getList(key: String): List<T>? {
        val raw = store[key] ?: return null
        return try {
            Json.decodeFromString(ListSerializer(serializer<T>()), raw)
        } catch (_: Exception) {
            null
        }
    }

    inline fun <reified T> setList(key: String, list: List<T>) {
        store[key] = Json.encodeToString(ListSerializer(serializer<T>()), list)
    }
}
