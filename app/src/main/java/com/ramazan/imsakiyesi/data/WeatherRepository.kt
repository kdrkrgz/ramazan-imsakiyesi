package com.ramazan.imsakiyesi.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

object WeatherRepository {
    private const val CACHE_PREFS = "weather_cache"
    private const val CACHE_WINDOW_MS = 3 * 60 * 60 * 1000L

    suspend fun getTemperatureText(
        context: Context,
        city: CityEntry,
        forceRefresh: Boolean
    ): String {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val keyBase = city.name
        val tsKey = "${keyBase}_ts"
        val tempKey = "${keyBase}_temp"
        val now = System.currentTimeMillis()

        val hasCached = prefs.contains(tempKey)
        val cachedTemp = if (hasCached) prefs.getFloat(tempKey, 0f).toDouble() else null
        val lastTs = prefs.getLong(tsKey, 0L)
        val cacheIsFresh = hasCached && (now - lastTs) < CACHE_WINDOW_MS

        if (!forceRefresh && cacheIsFresh) {
            return formatTemp(cachedTemp)
        }

        val freshTemp = fetchTemperature(city.lat, city.lon)
        if (freshTemp != null) {
            prefs.edit()
                .putFloat(tempKey, freshTemp.toFloat())
                .putLong(tsKey, now)
                .apply()
            return formatTemp(freshTemp)
        }

        return formatTemp(cachedTemp)
    }

    private suspend fun fetchTemperature(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return@withContext runCatching {
            connection.inputStream.bufferedReader().use { reader ->
                val body = reader.readText()
                val current = JSONObject(body).optJSONObject("current")
                current?.optDouble("temperature_2m")?.takeUnless { it.isNaN() }
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun formatTemp(temp: Double?): String {
        return temp?.let { "${it.roundToInt()}°C" } ?: "--°C"
    }
}
