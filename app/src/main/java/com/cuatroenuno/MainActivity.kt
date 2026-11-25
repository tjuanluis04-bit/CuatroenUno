package com.cuatroenuno

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF1B2733)
private val Paper = Color(0xFFEEF1F4)
private val Accents = listOf(
    Color(0xFF2F6FED), Color(0xFFE59A1F), Color(0xFF1F9D74), Color(0xFFD6426A)
)
private val CornerNames = listOf(
    "Arriba izquierda", "Arriba derecha", "Abajo izquierda", "Abajo derecha"
)

data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Ink, background = Paper, surface = Paper)
            ) { Screen() }
        }
    }
}

/* ---------- Lógica ---------- */

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun isFreeformReady(ctx: Context): Boolean {
    val feature = ctx.packageManager.hasSystemFeature("android.software.freeform_window_management")
    val setting = try {
        Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0) == 1
    } catch (_: Throwable) {
        false
    }
    return feature || setting
}

fun launchAll(ctx: Context, slots: List<String?>) {
    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val b = wm.currentWindowMetrics.bounds
    val w = b.width()
    val h = b.height()
    val rects = listOf(
        Rect(0, 0, w / 2, h / 2),
        Rect(w / 2, 0, w, h / 2),
        Rect(0, h / 2, w / 2, h),
        Rect(w / 2, h / 2, w, h)
    )
    val handler = Handler(Looper.getMainLooper())
    var delay = 0L
    slots.forEachIndexed { i, pkg ->
        if (pkg == null) return@forEachIndexed
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(ctx, "No se pudo abrir $pkg", Toast.LENGTH_SHORT).show()
            return@forEachIndexed
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val opts = ActivityOptions.makeBasic().setLaunchBounds(rects[i])
        try {
            // 5 = WINDOWING_MODE_FREEFORM (API oculta; si falla, se ignora)
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(opts, 5)
        } catch (_: Throwable) {
        }
        handler.postDelayed({
            try {
                ctx.startActivity(intent, opts.toBundle())
            } catch (_: Throwable) {
                Toast.makeText(ctx, "No se pudo abrir $pkg", Toast.LENGTH_SHORT).show()
            }
        }, delay)
        delay += 700L
    }
}

/* ---------- UI ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("slots", Context.MODE_PRIVATE) }
    val slots = remember {
        mutableStateListOf<String?>().apply { repeat(4) { add(prefs.getString("s$it", null)) } }
    }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var freeform by remember { mutableStateOf(isFreeformReady(ctx)) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { freeform = isFreeformReady(ctx) }

    val chosen = slots.count { it != null }
    val cfg = LocalConfiguration.current
    val ratio = cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.toFloat()

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("4 en 1", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Elige una app por esquina y ábrelas juntas en una sola pantalla.",
            fontSize = 15.sp, color = Ink.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Ink)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0..1) {
                            val i = row * 2 + col
                            Tile(
                                index = i,
                                app = apps.firstOrNull { it.pkg == slots[i] },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { picking = i }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!freeform) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(Modifier.padding(14.dp)) {
                    Text("Activa las ventanas flotantes", fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "En Opciones de desarrollador activa «Ventanas de forma libre» y " +
                            "«Forzar actividades redimensionables». Luego reinicia el teléfono.",
                        fontSize = 13.sp, color = Ink.copy(alpha = 0.75f)
                    )
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (_: Throwable) {
                            Toast.makeText(ctx, "Activa primero las Opciones de desarrollador", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Abrir opciones de desarrollador") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { launchAll(ctx, slots.toList()) },
            enabled = chosen >= 2,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)
        ) {
            Text(
                if (chosen < 2) "Elige al menos 2 apps" else "Abrir $chosen apps",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }

    picking?.let { i ->
        ModalBottomSheet(onDismissRequest = { picking = null }, containerColor = Paper) {
            Text(
                CornerNames[i],
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                if (slots[i] != null) {
                    item {
                        TextButton(
                            onClick = {
                                slots[i] = null
                                prefs.edit().remove("s$i").apply()
                                picking = null
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) { Text("Quitar app de esta esquina") }
                    }
                }
                items(apps, key = { it.pkg }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                slots[i] = app.pkg
                                prefs.edit().putString("s$i", app.pkg).apply()
                                picking = null
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun Tile(index: Int, app: AppInfo?, modifier: Modifier, onClick: () -> Unit) {
    val accent = Accents[index]
    val r = 30.dp
    val s = 8.dp
    val shape = when (index) {
        0 -> RoundedCornerShape(r, s, s, s)
        1 -> RoundedCornerShape(s, r, s, s)
        2 -> RoundedCornerShape(s, s, s, r)
        else -> RoundedCornerShape(s, s, r, s)
    }
    Box(
        modifier
            .clip(shape)
            .background(lerp(Color.White, accent, 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            if (app != null) {
                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    app.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            } else {
                Text("+", fontSize = 38.sp, fontWeight = FontWeight.Light, color = accent)
                Text(CornerNames[index], fontSize = 12.sp, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
    }
}
