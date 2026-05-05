package com.healthdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

// ── Colours (matching the web mockup) ──────────────────────────────────────
val GreenPrimary   = Color(0xFF1D9E75)
val GreenLight     = Color(0xFF9FE1CB)
val GreenBg        = Color(0xFFE1F5EE)
val OrangePrimary  = Color(0xFFD85A30)
val OrangeBg       = Color(0xFFFAECE7)
val BluePrimary    = Color(0xFF378ADD)
val BlueBg         = Color(0xFFE6F1FB)
val SurfaceGray    = Color(0xFFF5F5F5)
val CardBorder     = Color(0xFFE0E0E0)

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: DashboardViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val client = HealthConnectClient.getOrCreate(this)
        viewModel.checkPermissionsAndLoad(client)
    }

    // Health Connect permission contract
    private val healthPermissionLauncher = registerForActivityResult(
        HealthConnectClient.createRequestPermissionResultContract()
    ) {
        val client = HealthConnectClient.getOrCreate(this)
        viewModel.checkPermissionsAndLoad(client)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            viewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                viewModel.init(context)
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = GreenPrimary,
                    background = Color.White,
                    surface = Color.White,
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    when {
                        state.isLoading -> LoadingScreen()
                        state.healthConnectUnavailable -> UnavailableScreen()
                        state.needsPermission -> PermissionScreen {
                            healthPermissionLauncher.launch(PERMISSIONS)
                        }
                        else -> DashboardScreen(state)
                    }
                }
            }
        }
    }
}

// ── Loading ────────────────────────────────────────────────────────────────
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GreenPrimary)
    }
}

// ── Unavailable ────────────────────────────────────────────────────────────
@Composable
fun UnavailableScreen() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Health Connect not available", fontWeight = FontWeight.Medium, fontSize = 18.sp)
            Text("Please install Health Connect from the Play Store.", color = Color.Gray, fontSize = 14.sp)
        }
    }
}

// ── Permission ─────────────────────────────────────────────────────────────
@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(GreenBg),
                contentAlignment = Alignment.Center
            ) {
                Text("🏃", fontSize = 36.sp)
            }
            Text("HealthDash", fontWeight = FontWeight.Medium, fontSize = 24.sp)
            Text(
                "HealthDash needs access to your Health Connect data to show your activity dashboard.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Access", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

// ── Dashboard ──────────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(state: DashboardState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Good ${greeting()}", fontSize = 13.sp, color = Color.Gray)
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            val pct = (state.todaySteps * 100 / state.stepsGoal).toInt().coerceAtMost(100)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GreenBg,
            ) {
                Text("$pct% of goal", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, color = Color(0xFF0F6E56), fontWeight = FontWeight.Medium)
            }
        }

        // Rings card
        RingsCard(state)

        // Stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "🔥", formatNumber(state.todayCalories), "Calories")
            StatCard(Modifier.weight(1f), "📍", "%.1f km".format(state.todayDistanceKm), "Distance")
            StatCard(Modifier.weight(1f), "⏱", "${state.todayActiveMinutes} min", "Active time")
        }

        // Weekly chart
        if (state.weeklySteps.isNotEmpty()) {
            WeeklyChart(state.weeklySteps, state.stepsGoal.toLong())
        }

        // Recent activities
        if (state.recentExercises.isNotEmpty()) {
            Text("Recent activity", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray, letterSpacing = 0.08.sp)
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, CardBorder),
                color = Color.White,
            ) {
                Column {
                    state.recentExercises.forEachIndexed { i, ex ->
                        ActivityRow(ex)
                        if (i < state.recentExercises.lastIndex)
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Rings ──────────────────────────────────────────────────────────────────
@Composable
fun RingsCard(state: DashboardState) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, CardBorder),
        color = Color.White,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated rings
            val movePct = (state.todayActiveMinutes.toFloat() / state.moveMinutesGoal).coerceIn(0f, 1f)
            val heartPct = (state.heartPointsToday.toFloat() / 30f).coerceIn(0f, 1f)
            val stepsPct = (state.todaySteps.toFloat() / state.stepsGoal).coerceIn(0f, 1f)

            val moveAnim by animateFloatAsState(movePct, tween(1200, easing = FastOutSlowInEasing), label = "move")
            val heartAnim by animateFloatAsState(heartPct, tween(1200, 100, FastOutSlowInEasing), label = "heart")

            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12f
                    val gap = 16f
                    // Outer ring (move)
                    val r1 = (size.width / 2) - stroke / 2
                    drawArc(color = GreenBg, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(size.width / 2 - r1, size.height / 2 - r1), size = Size(r1 * 2, r1 * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color = GreenPrimary, startAngle = -90f, sweepAngle = 360f * moveAnim, useCenter = false, topLeft = Offset(size.width / 2 - r1, size.height / 2 - r1), size = Size(r1 * 2, r1 * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                    // Middle ring (heart)
                    val r2 = r1 - stroke - gap
                    drawArc(color = OrangeBg, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(size.width / 2 - r2, size.height / 2 - r2), size = Size(r2 * 2, r2 * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color = OrangePrimary, startAngle = -90f, sweepAngle = 360f * heartAnim, useCenter = false, topLeft = Offset(size.width / 2 - r2, size.height / 2 - r2), size = Size(r2 * 2, r2 * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatNumber(state.todaySteps), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text("steps", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Legend
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                RingLegendItem(GreenPrimary, "Move minutes", "${state.todayActiveMinutes} min", "Goal: ${state.moveMinutesGoal} min")
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                RingLegendItem(OrangePrimary, "Heart points", "${state.heartPointsToday} pts", "Goal: 30 pts")
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                RingLegendItem(BluePrimary, "Steps", formatNumber(state.todaySteps), "Goal: ${formatNumber(state.stepsGoal.toLong())}")
            }
        }
    }
}

@Composable
fun RingLegendItem(color: Color, label: String, value: String, goal: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(goal, fontSize = 11.sp, color = Color(0xFFAAAAAA))
        }
    }
}

// ── Stat card ──────────────────────────────────────────────────────────────
@Composable
fun StatCard(modifier: Modifier, icon: String, value: String, label: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = SurfaceGray) {
        Column(Modifier.padding(14.dp)) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ── Weekly chart ───────────────────────────────────────────────────────────
@Composable
fun WeeklyChart(weeklySteps: List<Pair<String, Long>>, goalSteps: Long) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, CardBorder),
        color = Color.White,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("THIS WEEK — STEPS", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray, letterSpacing = 0.08.sp)
            Spacer(Modifier.height(12.dp))
            val maxSteps = (weeklySteps.maxOfOrNull { it.second } ?: goalSteps).coerceAtLeast(goalSteps)
            Row(
                Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklySteps.forEachIndexed { i, (day, steps) ->
                    val isToday = i == weeklySteps.lastIndex
                    val frac = if (maxSteps > 0) steps.toFloat() / maxSteps else 0f
                    val animFrac by animateFloatAsState(frac, tween(1000, i * 80, FastOutSlowInEasing), label = "bar$i")
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animFrac)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isToday) GreenPrimary else GreenLight)
                        )
                        Text(day, fontSize = 10.sp, color = if (isToday) GreenPrimary else Color.Gray, fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("- - - ${formatNumber(goalSteps)} step goal", fontSize = 11.sp, color = Color.Gray)
                val avg = if (weeklySteps.isNotEmpty()) weeklySteps.sumOf { it.second } / weeklySteps.size else 0
                Text("avg ${formatNumber(avg)} / day", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// ── Activity row ───────────────────────────────────────────────────────────
@Composable
fun ActivityRow(ex: ExerciseInfo) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(GreenBg),
            contentAlignment = Alignment.Center
        ) {
            Text(exerciseEmoji(ex.name), fontSize = 16.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(ex.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${ex.timeLabel} · ${ex.durationMinutes} min", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 1.dp))
        }
        Text("+${ex.heartPoints} pts", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OrangePrimary)
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────
fun formatNumber(n: Long): String = "%,d".format(n)

fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
}

fun exerciseEmoji(name: String): String = when {
    name.contains("Run", true) -> "🏃"
    name.contains("Walk", true) -> "🚶"
    name.contains("Cycl", true) || name.contains("Bik", true) -> "🚴"
    name.contains("Swim", true) -> "🏊"
    name.contains("Strength", true) -> "🏋"
    name.contains("Yoga", true) -> "🧘"
    name.contains("Hik", true) -> "🥾"
    else -> "⚡"
}
