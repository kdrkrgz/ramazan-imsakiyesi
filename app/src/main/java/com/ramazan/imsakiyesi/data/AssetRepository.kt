package com.ramazan.imsakiyesi.data

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

object AssetRepository {
    private const val CITIES_FILE = "cities.json"
    private const val HADITHS_FILE = "hadiths.json"
    private const val PRAYER_TIMES_FILE = "prayer_times_2026.json"

    fun load(context: Context): AppData {
        val cities = loadCities(context)
        val hadiths = loadHadiths(context)
        val prayerTimesByCity = loadPrayerTimes(context)

        return AppData(
            cities = cities,
            prayerTimesByCity = prayerTimesByCity,
            hadiths = hadiths
        )
    }

    private fun loadCities(context: Context): List<CityEntry> {
        val json = context.assets.open(CITIES_FILE).bufferedReader().use { it.readText() }
        val citiesArray = JSONArray(json)
        return buildList(citiesArray.length()) {
            repeat(citiesArray.length()) { index ->
                val item = citiesArray.getJSONObject(index)
                add(
                    CityEntry(
                        name = item.getString("name"),
                        lat = item.getDouble("lat"),
                        lon = item.getDouble("lon")
                    )
                )
            }
        }
    }

    private fun loadHadiths(context: Context): List<HadithEntry> {
        val json = context.assets.open(HADITHS_FILE).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    HadithEntry(
                        id = item.getInt("id"),
                        type = item.getString("type"),
                        theme = item.getString("theme"),
                        text = item.getString("text"),
                        source = item.getString("source"),
                        reference = item.getString("reference")
                    )
                )
            }
        }
    }

    private fun loadPrayerTimes(context: Context): Map<String, List<PrayerTimesEntry>> {
        val json = context.assets.open(PRAYER_TIMES_FILE).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val items = buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    PrayerTimesEntry(
                        city = item.getString("city"),
                        date = LocalDate.parse(item.getString("date")),
                        fajr = item.getString("fajr"),
                        sunrise = item.getString("sunrise"),
                        dhuhr = item.getString("dhuhr"),
                        asr = item.getString("asr"),
                        maghrib = item.getString("maghrib"),
                        isha = item.getString("isha"),
                        isSpecial = item.optBoolean("isSpecial", false)
                    )
                )
            }
        }

        return items.groupBy { it.city }.mapValues { (_, value) ->
            value.sortedBy { it.date }
        }
    }
}
