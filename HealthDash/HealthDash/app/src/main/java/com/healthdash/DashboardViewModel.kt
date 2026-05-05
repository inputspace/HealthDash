package com.healthdash

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit

data class DashboardState(
    val isLoading: Boolean = true,
    val needsPermission: Boolean = false,
    val healthConnectUnavailable: Boolean = false,
    val todaySteps: Long = 0,
    val todayDistanceKm: Double = 0.0,
    val todayCalories: Long = 0,
    val todayActiveMinutes: Long = 0,
    val moveMinutesGoal: Int = 50,
    val stepsGoal: Int = 10000,
    val weeklySteps: List<Pair<String, Long>> = emptyList(),
    val recentExercises: List<ExerciseInfo> = emptyList(),
    val heartPointsToday: Int = 0,
)

data class ExerciseInfo(
    val name: String,
    val durationMinutes: Long,
    val distanceKm: Double?,
    val heartPoints: Int,
    val timeLabel: String,
)

val PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
)

class DashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    fun init(context: Context) {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            _state.value = _state.value.copy(
                isLoading = false,
                healthConnectUnavailable = true
            )
            return
        }
        val client = HealthConnectClient.getOrCreate(context)
        checkPermissionsAndLoad(client)
    }

    fun checkPermissionsAndLoad(client: HealthConnectClient) {
        viewModelScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(PERMISSIONS)) {
                _state.value = _state.value.copy(isLoading = false, needsPermission = true)
            } else {
                loadData(client)
            }
        }
    }

    private suspend fun loadData(client: HealthConnectClient) {
        _state.value = _state.value.copy(isLoading = true, needsPermission = false)

        val now = Instant.now()
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val weekStart = now.minus(6, ChronoUnit.DAYS)

        try {
            // Today's steps
            val stepsToday = client.aggregate(
                androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                )
            )[StepsRecord.COUNT_TOTAL] ?: 0L

            // Today's distance
            val distanceToday = client.aggregate(
                androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                )
            )[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0

            // Today's calories
            val caloriesToday = client.aggregate(
                androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                )
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toLong() ?: 0L

            // Weekly steps (day by day)
            val weeklyResponse = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(weekStart, now),
                    timeRangeSlicer = Duration.ofDays(1)
                )
            )
            val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
            val weeklySteps = weeklyResponse.mapIndexed { i, bucket ->
                val label = dayLabels.getOrElse(i) { "?" }
                val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L
                label to steps
            }

            // Recent exercises
            val exerciseRecords = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        now.minus(7, ChronoUnit.DAYS), now
                    )
                )
            ).records.takeLast(3).reversed()

            val exercises = exerciseRecords.map { session ->
                val duration = Duration.between(session.startTime, session.endTime).toMinutes()
                val timeLabel = when {
                    session.startTime.isAfter(todayStart) -> "Today"
                    session.startTime.isAfter(todayStart.minus(1, ChronoUnit.DAYS)) -> "Yesterday"
                    else -> {
                        val day = session.startTime.atZone(ZoneId.systemDefault()).dayOfWeek
                        day.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                    }
                }
                ExerciseInfo(
                    name = exerciseTypeName(session.exerciseType),
                    durationMinutes = duration,
                    distanceKm = null,
                    heartPoints = (duration / 2).toInt(),
                    timeLabel = timeLabel
                )
            }

            // Active minutes = exercise duration roughly
            val activeMinutes = exercises
                .filter { it.timeLabel == "Today" }
                .sumOf { it.durationMinutes }

            // Rough heart points based on active minutes
            val heartPoints = (activeMinutes / 2).toInt()

            _state.value = _state.value.copy(
                isLoading = false,
                todaySteps = stepsToday,
                todayDistanceKm = distanceToday,
                todayCalories = caloriesToday,
                todayActiveMinutes = activeMinutes,
                heartPointsToday = heartPoints,
                weeklySteps = weeklySteps,
                recentExercises = exercises
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun exerciseTypeName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        else -> "Exercise"
    }
}
