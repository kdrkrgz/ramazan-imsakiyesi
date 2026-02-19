package com.ramazan.imsakiyesi.data

import java.time.LocalDate

data class PrayerTimesEntry(
    val city: String,
    val date: LocalDate,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

data class HadithEntry(
    val id: Int,
    val type: String,
    val theme: String,
    val text: String,
    val source: String,
    val reference: String
)

data class CityEntry(
    val name: String,
    val lat: Double,
    val lon: Double
)

data class AppData(
    val cities: List<CityEntry>,
    val prayerTimesByCity: Map<String, List<PrayerTimesEntry>>,
    val hadiths: List<HadithEntry>
)
