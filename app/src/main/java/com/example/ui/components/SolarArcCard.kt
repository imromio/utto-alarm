package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.alarm.UttoAlarmScheduler
import com.example.data.DailySolarInfo
import com.example.solar.NoaaSolarCalculator
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.HorizonCoral
import com.example.ui.theme.NightDeep
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.StarlightMist
import com.example.ui.theme.TwilightViolet
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Main Date/Time & Solar Card:
 * Displays current date, prominent live clock time, dawn horizon solar arc,
 * Sunrise / Solar Noon / Sunset, and Day / Night total duration summary.
 */
@Composable
fun SolarArcHeroCard(
    now: ZonedDateTime,
    todaySolar: DailySolarInfo,
    modifier: Modifier = Modifier
) {
    val daylightProgress = remember(now.toLocalTime(), todaySolar) {
        NoaaSolarCalculator.calculateDaylightProgress(now.toLocalTime(), todaySolar)
    }
    val isDaytime = now.toLocalTime().isAfter(todaySolar.sunrise) &&
        now.toLocalTime().isBefore(todaySolar.sunset)

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d") }
    val timeOnlyFormatter = remember { DateTimeFormatter.ofPattern("h:mm") }
    val amPmFormatter = remember { DateTimeFormatter.ofPattern("a") }

    val dayMinutes = todaySolar.daylightMinutes.coerceIn(0, 1440)
    val nightMinutes = (1440 - dayMinutes).coerceIn(0, 1440)
    val daySummary = "${dayMinutes / 60}h ${dayMinutes % 60}m"
    val nightSummary = "${nightMinutes / 60}h ${nightMinutes % 60}m"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = NightDeep,
        border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.45f)),
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Layered Dawn Horizon artwork
            Image(
                painter = painterResource(id = R.drawable.img_dawn_horizon_1795498947141),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.42f)
            )

            // Atmospheric gradient scrim
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NightObsidian.copy(alpha = 0.72f),
                                NightObsidian.copy(alpha = 0.45f),
                                NightDeep.copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Current Date
                Text(
                    text = now.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = DawnGoldBright,
                    modifier = Modifier.testTag("hero_current_date")
                )

                Spacer(Modifier.height(4.dp))

                // Prominent Live Current Time
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = now.format(timeOnlyFormatter),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 58.sp,
                            lineHeight = 62.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = StarlightMist,
                        modifier = Modifier.testTag("hero_current_time")
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = now.format(amPmFormatter),
                        style = MaterialTheme.typography.headlineSmall,
                        color = DawnGoldBright,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Custom Canvas Solar Horizon Arc
                SolarArcCanvas(
                    progress = daylightProgress,
                    isDaytime = isDaytime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                Spacer(Modifier.height(10.dp))

                // Solar Events Strip: Sunrise, Solar Noon, Sunset + Day/Night summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NightObsidian.copy(alpha = 0.68f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SolarStatPill(
                            icon = Icons.Rounded.WbTwilight,
                            label = stringResource(R.string.sunrise_label),
                            value = UttoAlarmScheduler.formatTime12h(todaySolar.sunrise),
                            tint = HorizonCoral
                        )
                        SolarStatPill(
                            icon = Icons.Rounded.WbSunny,
                            label = stringResource(R.string.solar_noon_label),
                            value = UttoAlarmScheduler.formatTime12h(todaySolar.solarNoon),
                            tint = DawnGoldBright
                        )
                        SolarStatPill(
                            icon = Icons.Rounded.NightsStay,
                            label = stringResource(R.string.sunset_label),
                            value = UttoAlarmScheduler.formatTime12h(todaySolar.sunset),
                            tint = TwilightViolet
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Day: $daySummary · Night: $nightSummary",
                            style = MaterialTheme.typography.labelMedium,
                            color = CloudSecondary,
                            modifier = Modifier.testTag("hero_day_night_summary")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SolarStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = CloudSecondary
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = StarlightMist
        )
    }
}

@Composable
private fun SolarArcCanvas(
    progress: Float,
    isDaytime: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val horizontalPad = 20.dp.toPx()
        val horizonY = size.height - 14.dp.toPx()
        val arcWidth = size.width - horizontalPad * 2
        val arcHeight = size.height - 26.dp.toPx()

        // Horizon baseline
        drawLine(
            color = Color.White.copy(alpha = 0.16f),
            start = Offset(horizontalPad - 8.dp.toPx(), horizonY),
            end = Offset(size.width - horizontalPad + 8.dp.toPx(), horizonY),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        // Full solar arc track
        val trackPath = Path()
        val steps = 60
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = horizontalPad + arcWidth * t
            val y = horizonY - sin(t * PI).toFloat() * arcHeight
            if (i == 0) trackPath.moveTo(x, y) else trackPath.lineTo(x, y)
        }
        drawPath(
            path = trackPath,
            color = Color.White.copy(alpha = 0.18f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Elapsed illuminated arc
        val clamped = progress.coerceIn(0f, 1f)
        if (isDaytime && clamped > 0.01f) {
            val litPath = Path()
            val litSteps = (steps * clamped).toInt().coerceAtLeast(2)
            for (i in 0..litSteps) {
                val t = (i.toFloat() / litSteps) * clamped
                val x = horizontalPad + arcWidth * t
                val y = horizonY - sin(t * PI).toFloat() * arcHeight
                if (i == 0) litPath.moveTo(x, y) else litPath.lineTo(x, y)
            }
            drawPath(
                path = litPath,
                brush = Brush.horizontalGradient(listOf(HorizonCoral, DawnGold, DawnGoldBright)),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Sunrise & sunset horizon anchor dots
        drawCircle(
            color = DawnGold,
            radius = 4.dp.toPx(),
            center = Offset(horizontalPad, horizonY)
        )
        drawCircle(
            color = TwilightViolet,
            radius = 4.dp.toPx(),
            center = Offset(horizontalPad + arcWidth, horizonY)
        )

        // Glowing sun orb position
        val sunT = if (isDaytime) clamped else if (progress <= 0f) 0f else 1f
        val sunX = horizontalPad + arcWidth * sunT
        val sunY = horizonY - sin(sunT * PI).toFloat() * arcHeight

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    (if (isDaytime) DawnGold else TwilightViolet).copy(alpha = 0.55f),
                    Color.Transparent
                ),
                center = Offset(sunX, sunY),
                radius = 20.dp.toPx()
            ),
            radius = 20.dp.toPx(),
            center = Offset(sunX, sunY)
        )
        drawCircle(
            color = if (isDaytime) DawnGoldBright else TwilightViolet,
            radius = 6.5.dp.toPx(),
            center = Offset(sunX, sunY)
        )
        if (isDaytime) {
            for (angleDeg in 0 until 360 step 45) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val start = Offset(
                    sunX + (9.dp.toPx() * cos(rad)).toFloat(),
                    sunY + (9.dp.toPx() * sin(rad)).toFloat()
                )
                val end = Offset(
                    sunX + (12.dp.toPx() * cos(rad)).toFloat(),
                    sunY + (12.dp.toPx() * sin(rad)).toFloat()
                )
                drawLine(
                    color = DawnGoldBright.copy(alpha = 0.65f),
                    start = start,
                    end = end,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
