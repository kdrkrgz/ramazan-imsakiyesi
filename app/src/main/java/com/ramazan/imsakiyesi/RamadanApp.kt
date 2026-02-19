package com.ramazan.imsakiyesi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.ramazan.imsakiyesi.data.AppData
import com.ramazan.imsakiyesi.data.AppPreferences
import com.ramazan.imsakiyesi.data.AssetRepository
import com.ramazan.imsakiyesi.data.CityEntry
import com.ramazan.imsakiyesi.data.HadithEntry
import com.ramazan.imsakiyesi.data.PrayerTimesEntry
import com.ramazan.imsakiyesi.data.WeatherRepository
import com.ramazan.imsakiyesi.notifications.NotificationToggles
import com.ramazan.imsakiyesi.notifications.RamadanNotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.File
import java.io.FileOutputStream
import android.os.Build
import java.util.Locale
import kotlin.math.absoluteValue

private enum class AppTab {
    CITY_CHANGE, PRAYER_TIMES, SETTINGS, ABOUT
}

private data class NotificationPrefs(
    val prayer: Boolean = true,
    val imsak: Boolean = true,
    val iftar: Boolean = true
)

private data class PrayerUi(
    val name: String,
    val time: String,
    val icon: ImageVector
)

private data class CountdownUi(
    val label: String,
    val remaining: String
)

@Composable
fun RamadanApp() {
    val context = LocalContext.current
    var splashMinElapsed by remember { mutableStateOf(false) }

    val appData by produceState<AppData?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            AssetRepository.load(context)
        }
    }

    LaunchedEffect(Unit) {
        delay(1_000)
        splashMinElapsed = true
    }

    val shouldShowSplash = !splashMinElapsed || appData == null
    if (shouldShowSplash) {
        SplashScreen()
        return
    }

    val data = appData ?: return
    val savedSettings = remember(data.cities) { AppPreferences.load(context) }
    val initialCity = savedSettings.cityName?.takeIf { saved ->
        data.cities.any { it.name == saved }
    }
    val initialTab = if (initialCity == null) AppTab.CITY_CHANGE else AppTab.PRAYER_TIMES

    var activeTab by rememberSaveable { mutableStateOf(initialTab) }
    var selectedCity by rememberSaveable {
        mutableStateOf(initialCity)
    }
    var notificationPrefs by remember {
        mutableStateOf(
            NotificationPrefs(
                prayer = savedSettings.prayerNotifications,
                imsak = savedSettings.imsakNotifications,
                iftar = savedSettings.iftarNotifications
            )
        )
    }
    var weatherText by remember { mutableStateOf("--°C") }
    var weatherForceRefresh by remember { mutableStateOf(false) }
    var weatherRefreshNonce by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    val selectedCityEntry = remember(selectedCity, data.cities) {
        data.cities.firstOrNull { it.name == selectedCity }
    }

    LaunchedEffect(selectedCity, weatherRefreshNonce, selectedCityEntry) {
        selectedCityEntry?.let { city ->
            weatherText = WeatherRepository.getTemperatureText(
                context = context,
                city = city,
                forceRefresh = weatherForceRefresh
            )
        } ?: run {
            weatherText = "--°C"
        }
        weatherForceRefresh = false
    }

    LaunchedEffect(notificationPrefs) {
        if ((notificationPrefs.prayer || notificationPrefs.imsak || notificationPrefs.iftar) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(selectedCity, notificationPrefs, data.prayerTimesByCity) {
        val cityName = selectedCity
        AppPreferences.save(
            context = context,
            cityName = cityName,
            prayerNotifications = notificationPrefs.prayer,
            imsakNotifications = notificationPrefs.imsak,
            iftarNotifications = notificationPrefs.iftar
        )

        if (cityName != null) {
            RamadanNotificationScheduler.rescheduleForCity(
                context = context,
                city = cityName,
                entries = data.prayerTimesByCity[cityName].orEmpty(),
                toggles = NotificationToggles(
                    prayer = notificationPrefs.prayer,
                    imsak = notificationPrefs.imsak,
                    iftar = notificationPrefs.iftar
                )
            )
        } else {
            RamadanNotificationScheduler.cancelAll(context)
        }
    }

    val currentTime by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC),
        bottomBar = {
            BottomNav(
                activeTab = activeTab,
                onTabChange = { tab ->
                    if (selectedCity == null && tab != AppTab.CITY_CHANGE) {
                        Toast.makeText(context, "Lütfen şehir seçimi yapınız", Toast.LENGTH_SHORT).show()
                        activeTab = AppTab.CITY_CHANGE
                    } else {
                        activeTab = tab
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            when (activeTab) {
                AppTab.PRAYER_TIMES -> selectedCity?.let {
                    DashboardScreen(
                        appData = data,
                        selectedCity = it,
                        currentTime = currentTime,
                        weatherText = weatherText
                    )
                }

                AppTab.CITY_CHANGE -> CitySelectorScreen(
                    cities = data.cities,
                    onCitySelected = { city ->
                        weatherForceRefresh = true
                        selectedCity = city.name
                        weatherRefreshNonce++
                        activeTab = AppTab.PRAYER_TIMES
                    }
                )

                AppTab.SETTINGS -> selectedCity?.let { city ->
                    SettingsScreen(
                        prefs = notificationPrefs,
                        onPrefsChange = { notificationPrefs = it },
                        onGoToAbout = { activeTab = AppTab.ABOUT },
                        onSendTestNotifications = {
                            if (hasNotificationPermission(context)) {
                                RamadanNotificationScheduler.triggerAllTestNotifications(context, city)
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                Toast.makeText(context, "Bildirim izni gerekli", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                AppTab.ABOUT -> AboutScreen(onBack = { activeTab = AppTab.SETTINGS })
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Image(
        painter = painterResource(id = R.drawable.splash),
        contentDescription = "Ramazan Splash",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun DashboardScreen(
    appData: AppData,
    selectedCity: String,
    currentTime: LocalTime,
    weatherText: String,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val cityPrayerEntries = remember(selectedCity, appData.prayerTimesByCity) {
        appData.prayerTimesByCity[selectedCity].orEmpty()
    }
    val prayerEntry = remember(selectedCity, cityPrayerEntries, today) {
        cityPrayerEntries.firstOrNull { it.date == today } ?: findClosestEntry(cityPrayerEntries, today)
    }
    val nextDayEntry = remember(selectedCity, cityPrayerEntries, today) {
        cityPrayerEntries.firstOrNull { it.date == today.plusDays(1) }
    }
    val hadith = remember(today, appData.hadiths) {
        appData.hadiths.takeIf { it.isNotEmpty() }?.let { list ->
            list[(today.dayOfYear - 1) % list.size]
        }
    }
    val prayerCards = prayerEntry?.let { mapPrayerUi(it) }.orEmpty()
    val activePrayerName = remember(prayerCards, currentTime) { nextPrayerName(prayerCards, currentTime) }
    val countdown = remember(prayerEntry, nextDayEntry, currentTime, today) {
        calculateCountdown(todayEntry = prayerEntry, nextDayEntry = nextDayEntry, today = today, now = currentTime)
    }
    val todayLabel = remember(today) {
        today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy / EEEE", Locale("tr", "TR")))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val topScale = when {
            maxHeight < 700.dp -> 0.88f
            maxHeight < 760.dp -> 0.92f
            maxHeight < 840.dp -> 0.96f
            else -> 1f
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        scaleX = topScale
                        scaleY = topScale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedCity,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color(0xFF1F2937)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFEAB308)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(weatherText, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
                    }
                }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = countdown.label,
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = countdown.remaining,
                            color = Color(0xFF4F46E5),
                            fontSize = 66.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vakitler", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF1F2937))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0E7FF))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(todayLabel, color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        prayerCards.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { prayer ->
                                    PrayerCard(
                                        modifier = Modifier.weight(1f),
                                        prayer = prayer,
                                        isActive = prayer.name == activePrayerName
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    hadith?.let {
                        HadithCard(hadith = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerCard(
    prayer: PrayerUi,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFFEEF2FF) else Color.White
        ),
        border = BorderStroke(1.dp, if (isActive) Color(0xFF818CF8) else Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = prayer.icon,
                contentDescription = prayer.name,
                tint = if (isActive) Color(0xFF4F46E5) else Color(0xFF60A5FA),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = prayer.time,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = if (isActive) Color(0xFF4338CA) else Color(0xFF111827)
            )
            Text(
                text = prayer.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF)
            )
        }
    }
}

@Composable
private fun HadithCard(hadith: HadithEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val title = if (hadith.type.equals("verse", ignoreCase = true)) "GÜNÜN AYETİ" else "GÜNÜN HADİSİ"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF4F46E5))
                        .clickable { shareHadithCard(context, hadith) }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Paylaş",
                        tint = Color.White
                    )
                }
            }
            Text(
                text = "${hadith.source}, ${hadith.reference}",
                color = Color(0xFF4F46E5),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "\"${hadith.text}\"",
                color = Color(0xFF374151),
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun CitySelectorScreen(
    cities: List<CityEntry>,
    onCitySelected: (CityEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchTerm by rememberSaveable { mutableStateOf("") }
    val filteredCities = remember(cities, searchTerm) {
        cities.filter { it.name.contains(searchTerm, ignoreCase = true) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFEEF2FF))
                    .padding(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Hoş Geldin Ya Şehr-i Ramazan",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color(0xFF111827),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Hayırlı Ramazanlar Dileriz",
                color = Color(0xFF6366F1),
                fontWeight = FontWeight.Medium
            )
        }

        OutlinedTextField(
            value = searchTerm,
            onValueChange = { searchTerm = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Color(0xFF818CF8)
                )
            },
            placeholder = { Text("Şehir Seçiniz") },
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Şehirler", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111827))
            Text("", color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredCities) { city ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCitySelected(city) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFEEF2FF))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(city.name, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("TÜRKİYE", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                        }
                        Icon(
                            imageVector = Icons.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    prefs: NotificationPrefs,
    onPrefsChange: (NotificationPrefs) -> Unit,
    onGoToAbout: () -> Unit,
    onSendTestNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bildirimler",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = Color(0xFF111827)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Bildirim Tercihleri", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
            }
        }


        NotificationRow(
            title = "Namaz Vakit Bildirimleri",
            description = "Tüm vakitler için hatırlatıcı al",
            checked = prefs.prayer,
            onCheckedChange = { onPrefsChange(prefs.copy(prayer = it)) }
        )
        NotificationRow(
            title = "İmsak Bildirimi",
            description = "Sahur vakti için özel uyarı",
            checked = prefs.imsak,
            onCheckedChange = { onPrefsChange(prefs.copy(imsak = it)) }
        )
        NotificationRow(
            title = "İftar Bildirimi",
            description = "Oruç açma vakti hatırlatıcısı",
            checked = prefs.iftar,
            onCheckedChange = { onPrefsChange(prefs.copy(iftar = it)) }
        )

        Text("GENEL AYARLAR", fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF), fontSize = 12.sp)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGoToAbout() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4F46E5))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Uygulama Hakkında", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Icon(imageVector = Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = Color.White)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSendTestNotifications() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEEF2FF))
                            .padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Notifications, contentDescription = null, tint = Color(0xFF4F46E5))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tüm Bildirimleri Test Et", color = Color(0xFF111827), fontWeight = FontWeight.Bold)
                }
                Icon(imageVector = Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = Color(0xFF9CA3AF))
            }
        }
    }
}

@Composable
private fun NotificationRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF111827), fontSize = 14.sp)
                Text(description, color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Geri"
                )
            }
            Text(
                text = "Uygulama Hakkında",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.width(36.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFEEF2FF))
                    .padding(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ramazan İmsakiyesi", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color(0xFF111827))
            Text("Sürüm 1.0.0", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
        }

        InfoCard(
            title = "LİSANS",
            content = "Bu proje MIT lisansı ile sunulmaktadır. Kullanabilir, geliştirebilir ve dağıtabilirsiniz.",
            icon = Icons.Outlined.Info
        )
        InfoCard(
            title = "KATKILAR",
            content = "Açık kaynak katkıları ve proje deposu: github.com/kdrkrgz",
            icon = Icons.Outlined.Share
        )
        InfoCard(
            title = "İLETİŞİM",
            content = "Destek ve geri bildirim: karagoz.projects@gmail.com",
            icon = Icons.Outlined.LocationOn
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hayırlı Ramazanlar",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color(0xAA4F46E5),
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF9CA3AF)
            )
            Text(title, color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(content, textAlign = TextAlign.Center, color = Color(0xFF374151))
        }
    }
}

@Composable
private fun BottomNav(
    activeTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsSelected = activeTab == AppTab.SETTINGS || activeTab == AppTab.ABOUT
    NavigationBar(
        modifier = modifier,
        containerColor = Color.White.copy(alpha = 0.95f)
    ) {
        NavigationBarItem(
            selected = activeTab == AppTab.CITY_CHANGE,
            onClick = { onTabChange(AppTab.CITY_CHANGE) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF4F46E5),
                selectedTextColor = Color(0xFF4F46E5),
                indicatorColor = Color(0xFFE0E7FF),
                unselectedIconColor = Color(0xFF9CA3AF),
                unselectedTextColor = Color(0xFF9CA3AF)
            ),
            icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
            label = { Text("ŞEHİR DEĞİŞTİR", fontSize = 10.sp, maxLines = 1) }
        )
        NavigationBarItem(
            selected = activeTab == AppTab.PRAYER_TIMES,
            onClick = { onTabChange(AppTab.PRAYER_TIMES) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF4F46E5),
                selectedTextColor = Color(0xFF4F46E5),
                indicatorColor = Color(0xFFE0E7FF),
                unselectedIconColor = Color(0xFF9CA3AF),
                unselectedTextColor = Color(0xFF9CA3AF)
            ),
            icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            label = { Text("İMSAKİYE", fontSize = 10.sp) }
        )
        NavigationBarItem(
            selected = settingsSelected,
            onClick = { onTabChange(AppTab.SETTINGS) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF4F46E5),
                selectedTextColor = Color(0xFF4F46E5),
                indicatorColor = Color(0xFFE0E7FF),
                unselectedIconColor = Color(0xFF9CA3AF),
                unselectedTextColor = Color(0xFF9CA3AF)
            ),
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text("AYARLAR", fontSize = 10.sp) }
        )
    }
}

private fun findClosestEntry(entries: List<PrayerTimesEntry>, targetDate: LocalDate): PrayerTimesEntry? {
    return entries.minByOrNull { ChronoUnit.DAYS.between(it.date, targetDate).absoluteValue }
}

private fun mapPrayerUi(entry: PrayerTimesEntry): List<PrayerUi> {
    return listOf(
        PrayerUi("İMSAK", entry.fajr, Icons.Outlined.DarkMode),
        PrayerUi("GÜNEŞ", entry.sunrise, Icons.Outlined.WbSunny),
        PrayerUi("ÖĞLE", entry.dhuhr, Icons.Outlined.WbSunny),
        PrayerUi("İKİNDİ", entry.asr, Icons.Outlined.Cloud),
        PrayerUi("AKŞAM", entry.maghrib, Icons.Outlined.WbTwilight),
        PrayerUi("YATSI", entry.isha, Icons.Outlined.Nightlight)
    )
}

private fun nextPrayerName(prayers: List<PrayerUi>, now: LocalTime): String? {
    if (prayers.isEmpty()) return null
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return prayers.firstOrNull { prayer ->
        runCatching { LocalTime.parse(prayer.time, formatter) }.getOrNull()?.let { it > now } == true
    }?.name ?: prayers.first().name
}

private fun calculateCountdown(
    todayEntry: PrayerTimesEntry?,
    nextDayEntry: PrayerTimesEntry?,
    today: LocalDate,
    now: LocalTime
): CountdownUi {
    if (todayEntry == null) {
        return CountdownUi("VAKTE KALAN SÜRE", "--:--:--")
    }
    val imsakToday = LocalTime.parse(todayEntry.fajr)
    val iftarToday = LocalTime.parse(todayEntry.maghrib)
    val nowDateTime = LocalDateTime.of(today, now)

    val (label, targetDateTime) = when {
        now.isBefore(imsakToday) -> {
            "İMSAK VAKTİNE KALAN SÜRE" to LocalDateTime.of(today, imsakToday)
        }

        now.isBefore(iftarToday) -> {
            "İFTARA KALAN SÜRE" to LocalDateTime.of(today, iftarToday)
        }

        else -> {
            val nextImsak = LocalTime.parse((nextDayEntry ?: todayEntry).fajr)
            "İMSAK VAKTİNE KALAN SÜRE" to LocalDateTime.of(today.plusDays(1), nextImsak)
        }
    }

    val remaining = Duration.between(nowDateTime, targetDateTime)
    return CountdownUi(label = label, remaining = formatDuration(remaining))
}

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun shareHadithCard(context: Context, hadith: HadithEntry) {
    val title = if (hadith.type.equals("verse", ignoreCase = true)) "GÜNÜN AYETİ" else "GÜNÜN HADİSİ"
    runCatching {
        val bitmap = buildShareBitmap(title = title, hadith = hadith)
        val file = File(context.cacheDir, "shared_hadith_card.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Görseli paylaş")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "Paylaşım hazırlanamadı.", Toast.LENGTH_SHORT).show()
    }
}

private fun buildShareBitmap(title: String, hadith: HadithEntry): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#F8FAFC") }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pagePaint)

    val cardRect = RectF(120f, 190f, width - 120f, height - 240f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#EEF2FF") }
    canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#A5B4FC")
        textSize = 34f
        isFakeBoldText = true
        letterSpacing = 0.08f
    }
    canvas.drawText(title, cardRect.left + 44f, cardRect.top + 72f, titlePaint)

    val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#4F46E5")
        textSize = 38f
        isFakeBoldText = true
    }
    canvas.drawText("${hadith.source}, ${hadith.reference}", cardRect.left + 44f, cardRect.top + 138f, sourcePaint)

    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#374151")
        textSize = 58f
    }
    val bodyText = "\"${hadith.text}\""
    val bodyWidth = (cardRect.width() - 96f).toInt()
    val textLayout = StaticLayout.Builder
        .obtain(bodyText, 0, bodyText.length, bodyPaint, bodyWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(16f, 1f)
        .build()
    canvas.save()
    canvas.translate(cardRect.left + 48f, cardRect.top + 200f)
    textLayout.draw(canvas)
    canvas.restore()

    return bitmap
}
