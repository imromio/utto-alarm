package com.example.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.UttoPreferencesRepository
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.HorizonCoral
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.StarlightMist
import com.example.ui.theme.UttoTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Full-screen lock-screen wake-up Activity supporting Android 9 (API 28) through latest Android.
 */
class AlarmRingingActivity : ComponentActivity() {

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenWakeup()

        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(AlarmRingingService.ACTION_RINGING_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val alarmLabel = intent.getStringExtra(UttoAlarmScheduler.EXTRA_ALARM_LABEL)
            ?: "Sunrise Wake-Up"
        val sunriseText = intent.getStringExtra(UttoAlarmScheduler.EXTRA_SUNRISE_TIME_TEXT)
            ?: ""
        val isSnooze = intent.getBooleanExtra(UttoAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val isTest = intent.getBooleanExtra(UttoAlarmScheduler.EXTRA_IS_TEST, false)

        setContent {
            UttoTheme(darkTheme = true) {
                var snoozeMinutes by remember { mutableIntStateOf(10) }
                LaunchedEffect(Unit) {
                    val repo = UttoPreferencesRepository(applicationContext)
                    snoozeMinutes = repo.getSettings().snoozeDurationMinutes
                }

                // Prevent accidental back dismissal while alarm is ringing
                BackHandler(enabled = !isTest) { }

                AlarmRingingScreen(
                    alarmLabel = alarmLabel,
                    sunriseText = sunriseText,
                    snoozeMinutes = snoozeMinutes,
                    isSnooze = isSnooze,
                    isTest = isTest,
                    onSnooze = {
                        sendServiceAction(AlarmRingingService.ACTION_SNOOZE)
                        finishAndRemoveTask()
                    },
                    onDismiss = {
                        sendServiceAction(AlarmRingingService.ACTION_DISMISS)
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    private fun configureLockScreenWakeup() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        keyguardManager?.requestDismissKeyguard(this, null)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun AlarmRingingScreen(
    alarmLabel: String,
    sunriseText: String,
    snoozeMinutes: Int,
    isSnooze: Boolean,
    isTest: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dawn_pulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val chevronOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevron_bounce"
    )

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = -170f

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm") }
    val amPmFormatter = remember { DateTimeFormatter.ofPattern("a") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightObsidian)
    ) {
        // Atmospheric dawn artwork backdrop
        Image(
            painter = painterResource(id = R.drawable.img_dawn_horizon_1795498947141),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f)
        )

        // Deep gradient scrim for crisp legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NightObsidian.copy(alpha = 0.78f),
                            NightObsidian.copy(alpha = 0.35f),
                            NightObsidian.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // Pulsing radiant sun aura
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.34f)
            val baseRadius = size.minDimension * 0.36f * haloScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DawnGold.copy(alpha = 0.34f),
                        HorizonCoral.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = DawnGold.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DawnGold.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WbSunny,
                            contentDescription = null,
                            tint = DawnGoldBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                isTest -> stringResource(R.string.ringing_test_badge)
                                isSnooze -> stringResource(R.string.ringing_snoozed_badge)
                                else -> "UTTO DAWN ALARM"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = DawnGoldBright
                        )
                    }
                }
            }

            // Center giant clock & sunrise details
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = now.format(timeFormatter),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 82.sp,
                            lineHeight = 86.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = StarlightMist
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = now.format(amPmFormatter),
                        style = MaterialTheme.typography.headlineMedium,
                        color = DawnGoldBright,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = alarmLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = StarlightMist,
                    textAlign = TextAlign.Center
                )

                if (sunriseText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sunrise today at $sunriseText",
                        style = MaterialTheme.typography.labelMedium,
                        color = DawnGoldBright.copy(alpha = 0.9f)
                    )
                }
            }

            // Bottom Snooze + Swipe-Up Dismiss controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Button(
                    onClick = onSnooze,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                        contentColor = StarlightMist
                    ),
                    modifier = Modifier
                        .height(58.dp)
                        .fillMaxWidth(0.74f)
                        .testTag("ringing_snooze_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Snooze,
                        contentDescription = null,
                        tint = DawnGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.ringing_snooze_button, snoozeMinutes),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Swipe-up dismiss interactive track
                val swipeProgress = (dragOffsetY / dismissThresholdPx).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(112.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    DawnGold.copy(alpha = 0.14f + 0.25f * swipeProgress),
                                    HorizonCoral.copy(alpha = 0.22f + 0.25f * swipeProgress)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = DawnGold.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetY <= dismissThresholdPx) {
                                        onDismiss()
                                    } else {
                                        dragOffsetY = 0f
                                    }
                                },
                                onDragCancel = { dragOffsetY = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    dragOffsetY = (dragOffsetY + dragAmount).coerceIn(-220f, 0f)
                                    if (dragOffsetY <= dismissThresholdPx) {
                                        onDismiss()
                                    }
                                }
                            )
                        }
                        .testTag("ringing_swipe_dismiss_track"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset { IntOffset(0, (dragOffsetY * 0.45f).roundToInt()) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .offset(y = chevronOffset.dp)
                                .clip(CircleShape)
                                .background(DawnGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.ringing_swipe_dismiss),
                                tint = NightObsidian,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.ringing_swipe_dismiss),
                            style = MaterialTheme.typography.labelLarge,
                            color = StarlightMist
                        )
                    }
                }

                // Accessible tap fallback for TalkBack & quick dismiss
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("ringing_dismiss_fallback_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = StarlightMist.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ringing_dismiss_fallback),
                        style = MaterialTheme.typography.labelMedium,
                        color = StarlightMist.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}
