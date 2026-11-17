package com.pulsehud

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONObject

private val Bg = Color(0xFF101012)
private val SurfaceCard = Color(0xFF1A1A1E)
private val SurfaceRaised = Color(0xFF242429)
private val Accent = Color(0xFFFF8A45)
private val Muted = Color(0xFF9A9AA3)
private val White = Color(0xFFF4F4F5)

class MainActivity : ComponentActivity() {
    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                HudPrefs.put(this, "font_uri", uri.toString())
                HudPrefs.put(this, "font_name", uri.lastPathSegment ?: "Imported font")
            } catch (_: Exception) { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorState.restore(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, background = Bg, surface = SurfaceCard,
                    onBackground = White, onSurface = White
                )
            ) {
                PulseDashboard(
                    onStart = { startOverlay() },
                    onStop = { sendOverlay(OverlayService.ACTION_STOP) },
                    onEdit = { sendOverlay(OverlayService.ACTION_EDIT) },
                    onDone = { sendOverlay(OverlayService.ACTION_LIVE) },
                    onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) }
                )
            }
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val i = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        ContextCompat.startForegroundService(this, i)
    }

    private fun sendOverlay(action: String) {
        if (action == OverlayService.ACTION_STOP) {
            startService(Intent(this, OverlayService::class.java).setAction(action))
        } else {
            startService(Intent(this, OverlayService::class.java).setAction(action))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PulseDashboard(
    onStart: () -> Unit, onStop: () -> Unit, onEdit: () -> Unit, onDone: () -> Unit,
    onAccessibility: () -> Unit, onFont: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { HudPrefs.prefs(context) }
    var showCps by remember { mutableStateOf(prefs.getBoolean("show_cps", true)) }
    var showFps by remember { mutableStateOf(prefs.getBoolean("show_fps", true)) }
    var showBattery by remember { mutableStateOf(prefs.getBoolean("show_battery", true)) }
    var interval by remember { mutableFloatStateOf(prefs.getInt("interval_ms", 1000).toFloat()) }
    var fontSize by remember { mutableFloatStateOf(prefs.getInt("font_size", 14).toFloat()) }
    var opacity by remember { mutableFloatStateOf(prefs.getInt("opacity", 82).toFloat()) }
    var alignment by remember { mutableStateOf(HudPrefs.str(context, "alignment", "left")) }
    var colorHex by remember { mutableStateOf(HudPrefs.str(context, "text_color", "#F4F4F5")) }
    var layout by remember { mutableStateOf(HudPrefs.str(context, "layout", "horizontal")) }
    var customName by remember { mutableStateOf("") }
    var presetNames by remember { mutableStateOf(prefs.getStringSet("preset_names", emptySet())?.toList() ?: emptyList()) }
    var selectedPreset by remember { mutableStateOf("Gaming") }
    var history by remember { mutableStateOf(prefs.getBoolean("history", false)) }
    var cps by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(MonitorState.hudFps) }
    var batteryPct by remember { mutableIntStateOf(-1) }
    var batteryTemp by remember { mutableStateOf("—") }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun save(key: String, value: Any) = HudPrefs.put(context, key, value)
    fun applyPreset(name: String) {
        selectedPreset = name
        val defaults = when (name) {
            "Minimal" -> mapOf("text_color" to "#F4F4F5", "font_size" to 12, "opacity" to 65, "layout" to "horizontal")
            "Neon" -> mapOf("text_color" to "#FF8A45", "font_size" to 16, "opacity" to 88, "layout" to "vertical")
            "Glass" -> mapOf("text_color" to "#DCE7F5", "font_size" to 14, "opacity" to 42, "layout" to "horizontal")
            "Terminal" -> mapOf("text_color" to "#8DFF9A", "font_size" to 13, "opacity" to 94, "layout" to "vertical")
            else -> mapOf("text_color" to "#F4F4F5", "font_size" to 15, "opacity" to 82, "layout" to "horizontal")
        }
        colorHex = defaults["text_color"] as String
        fontSize = (defaults["font_size"] as Int).toFloat()
        opacity = (defaults["opacity"] as Int).toFloat()
        layout = defaults["layout"] as String
        defaults.forEach { (k, v) -> save(k, v) }
        save("preset_base", name)
    }
    fun snapshot(): JSONObject = JSONObject().apply {
        put("show_cps", showCps); put("show_fps", showFps); put("show_battery", showBattery)
        put("interval_ms", interval.toInt()); put("font_size", fontSize.toInt())
        put("opacity", opacity.toInt()); put("alignment", alignment)
        put("text_color", colorHex); put("layout", layout)
    }

    LaunchedEffect(Unit) {
        while (true) {
            cps = MonitorState.currentCps(interval.toLong())
            fps = MonitorState.hudFps
            val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra("level", -1) ?: -1
            val scale = battery?.getIntExtra("scale", 100) ?: 100
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val temp = battery?.getIntExtra("temperature", -1) ?: -1
            batteryTemp = if (temp >= 0) "%.1f°C".format(temp / 10f) else "Unavailable"
            accessibilityEnabled = try {
                val enabled = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                enabled.contains(context.packageName, ignoreCase = true)
            } catch (_: Exception) { false }
            overlayPermission = Settings.canDrawOverlays(context)
            delay(500)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("PulseHUD", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("PERFORMANCE, AT A GLANCE", color = Muted, fontSize = 10.sp, letterSpacing = 1.7.sp)
                }
                Surface(color = Color(0xFF2B211B), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(Accent, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text("V2 BETA", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("LIVE MONITOR")
                        Text("UPDATING", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("CURRENT CPS", cps.toString(), "rolling ${interval.toInt()} ms", Modifier.weight(1f), Accent)
                        MetricCard("HUD FPS", fps.toString(), "overlay refresh", Modifier.weight(1f), White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("PEAK CPS", MonitorState.peakCps.toString(), "this session", Modifier.weight(1f), White)
                        MetricCard("TOTAL CLICKS", MonitorState.totalClicks.toString(), "saved total", Modifier.weight(1f), White)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onStart, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Start HUD")
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(5.dp)); Text("Stop")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit position") }
                        OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done editing") }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("DEVICE")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("BATTERY", if (batteryPct >= 0) "$batteryPct%" else "—", "remaining", Modifier.weight(1f), White)
                        MetricCard("TEMPERATURE", batteryTemp, "battery sensor", Modifier.weight(1f), White)
                    }
                    Text("FPS note: HUD FPS measures PulseHUD's own overlay refresh loop. It is not the game's FPS.", color = Muted, fontSize = 11.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("CLICK TRACKING")
                    StatusLine("Overlay permission", overlayPermission, "Allow display over other apps")
                    StatusLine("Accessibility service", accessibilityEnabled, "Enable PulseHUD in Android Accessibility settings")
                    Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised)) {
                        Icon(Icons.Default.TouchApp, null); Spacer(Modifier.width(7.dp)); Text("Open accessibility settings")
                    }
                    Surface(color = SurfaceRaised, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("TEST THE COUNTER", color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Tap here to verify the CPS calculation. This test only counts taps inside this test area; other apps depend on accessibility events being exposed.", color = Muted, fontSize = 11.sp)
                            Button(onClick = { MonitorState.recordClick(context) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
                                Text("TAP TEST · +1 CLICK")
                            }
                        }
                    }
                    Text("Important: Android does not let an ordinary app read every raw tap in every game. Many games expose no accessibility click events, so their CPS may stay at zero.", color = Color(0xFFFFC18E), fontSize = 11.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("WIDGETS")
                    ToggleRow("CPS counters", showCps) { showCps = it; save("show_cps", it) }
                    ToggleRow("HUD FPS", showFps) { showFps = it; save("show_fps", it) }
                    ToggleRow("Battery + temperature", showBattery) { showBattery = it; save("show_battery", it) }
                    Text("CPS rolling interval · ${interval.toInt()} ms", color = Muted, fontSize = 12.sp)
                    Slider(value = interval, onValueChange = { interval = it }, valueRange = 250f..5000f, onValueChangeFinished = { save("interval_ms", interval.toInt()) })
                    Text("Layout", color = Muted, fontSize = 12.sp)
                    ChoiceRow(listOf("horizontal", "vertical", "widgets"), layout) { layout = it; save("layout", it) }
                    Text("Layout selection is saved; this V2 overlay currently renders the enabled metrics as one compact panel.", color = Muted, fontSize = 10.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("APPEARANCE")
                    Text("Preset base", color = Muted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Minimal", "Gaming", "Neon", "Glass", "Terminal").forEach { name ->
                            FilterChip(selected = selectedPreset == name, onClick = { applyPreset(name) }, label = { Text(name) })
                        }
                    }
                    OutlinedTextField(
                        value = colorHex, onValueChange = { colorHex = it.uppercase() },
                        label = { Text("Text color · HEX") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            val c = parseHex(colorHex)
                            Box(Modifier.padding(end = 10.dp).size(22.dp).background(c, CircleShape).border(1.dp, Muted, CircleShape))
                        }
                    )
                    Button(onClick = { if (parseHexOrNull(colorHex) != null) save("text_color", colorHex) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised)) {
                        Icon(Icons.Default.Palette, null); Spacer(Modifier.width(6.dp)); Text("Apply color")
                    }
                    Text("Quick colors", color = Muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("#F4F4F5", "#FF8A45", "#8DFF9A", "#7DCBFF", "#FF78B7", "#D9C4FF").forEach { hex ->
                            Box(Modifier.size(31.dp).background(parseHex(hex), CircleShape).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).clickable { colorHex = hex; save("text_color", hex) })
                        }
                    }
                    Text("Font size · ${fontSize.toInt()} sp", color = Muted, fontSize = 12.sp)
                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 9f..30f, onValueChangeFinished = { save("font_size", fontSize.toInt()) })
                    Text("Background opacity · ${opacity.toInt()}%", color = Muted, fontSize = 12.sp)
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..100f, onValueChangeFinished = { save("opacity", opacity.toInt()) })
                    Text("Text alignment", color = Muted, fontSize = 12.sp)
                    ChoiceRow(listOf("left", "center", "right"), alignment) { alignment = it; save("alignment", it) }
                    OutlinedButton(onClick = onFont, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FontDownload, null); Spacer(Modifier.width(6.dp)); Text("Import TTF / OTF")
                    }
                    Text("Imported font URI is retained, but custom font rendering is not wired into the floating overlay yet.", color = Muted, fontSize = 10.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("PRESET LIBRARY")
                    OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("Preset name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (customName.isNotBlank()) {
                            val name = customName.trim()
                            val all = prefs.getString("custom_presets_json", "{}") ?: "{}"
                            val obj = try { JSONObject(all) } catch (_: Exception) { JSONObject() }
                            obj.put(name, snapshot())
                            save("custom_presets_json", obj.toString())
                            presetNames = obj.keys().asSequence().toList().sorted()
                            save("preset_names", presetNames.toSet())
                            customName = ""
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
                        Text("Save current settings as preset")
                    }
                    if (presetNames.isEmpty()) Text("No custom presets saved yet.", color = Muted, fontSize = 11.sp)
                    presetNames.forEach { name ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = White, fontSize = 13.sp)
                            TextButton(onClick = {
                                val all = try { JSONObject(prefs.getString("custom_presets_json", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
                                val obj = all.optJSONObject(name) ?: JSONObject()
                                showCps = obj.optBoolean("show_cps", showCps); showFps = obj.optBoolean("show_fps", showFps)
                                showBattery = obj.optBoolean("show_battery", showBattery)
                                interval = obj.optInt("interval_ms", interval.toInt()).toFloat()
                                fontSize = obj.optInt("font_size", fontSize.toInt()).toFloat()
                                opacity = obj.optInt("opacity", opacity.toInt()).toFloat()
                                alignment = obj.optString("alignment", alignment)
                                colorHex = obj.optString("text_color", colorHex)
                                layout = obj.optString("layout", layout)
                                save("show_cps", showCps); save("show_fps", showFps); save("show_battery", showBattery)
                                save("interval_ms", interval.toInt()); save("font_size", fontSize.toInt())
                                save("opacity", opacity.toInt()); save("alignment", alignment)
                                save("text_color", colorHex); save("layout", layout)
                            }) { Text("Apply", color = Accent) }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("SESSION & PRIVACY")
                    ToggleRow("Save session history (reserved for session records)", history) { history = it; save("history", it) }
                    OutlinedButton(onClick = { MonitorState.resetSession(context) }, modifier = Modifier.fillMaxWidth()) { Text("Reset click counters") }
                    Text("All settings and the total-click counter stay on this device. No account or cloud upload is used.", color = Muted, fontSize = 10.sp)
                }
            }

            Text("PulseHUD 0.2.0 · Android 10+ · source preview", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}

@Composable
private fun MetricCard(title: String, value: String, hint: String, modifier: Modifier, valueColor: Color) {
    Surface(modifier, color = SurfaceRaised, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, color = valueColor, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(hint, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = White, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusLine(label: String, enabled: Boolean, hint: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (enabled) Color(0xFF7DDB9A) else Accent)
        Spacer(Modifier.width(9.dp))
        Column {
            Text(label, color = White, fontSize = 12.sp)
            Text(if (enabled) "Enabled" else hint, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = selected == option
            Box(
                Modifier.weight(1f).background(if (active) Accent else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(option) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(option.replaceFirstChar { it.uppercase() }, color = if (active) Color.Black else White, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

private fun parseHexOrNull(hex: String): Int? = try {
    val clean = hex.trim().removePrefix("#")
    if (clean.length == 6) android.graphics.Color.parseColor("#$clean") else null
} catch (_: Exception) { null }

private fun parseHex(hex: String): Color = Color(parseHexOrNull(hex) ?: android.graphics.Color.WHITE)
