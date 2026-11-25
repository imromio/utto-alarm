package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.UttoAlarmScheduler
import com.example.data.AlarmMode
import com.example.data.UttoSettings
import com.example.solar.NoaaSolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read Utto app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Utto", appName)
    }

    @Test
    fun `NOAA solar calculator computes accurate San Francisco summer solstice sunrise`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val info = NoaaSolarCalculator.calculateDay(
            date = LocalDate.of(2026, 6, 21),
            latitude = 37.7749,
            longitude = -122.4194,
            zoneId = zone
        )
        // San Francisco sunrise on June 21 is ~05:48 PDT, sunset ~20:35 PDT
        assertTrue("Expected sunrise around 05:45-05:52, got ${info.sunrise}",
            info.sunrise.isAfter(LocalTime.of(5, 42)) && info.sunrise.isBefore(LocalTime.of(5, 55))
        )
        assertTrue("Expected sunset around 20:30-20:40, got ${info.sunset}",
            info.sunset.isAfter(LocalTime.of(20, 28)) && info.sunset.isBefore(LocalTime.of(20, 42))
        )
        assertTrue(info.civilDawn.isBefore(info.sunrise))
        assertTrue(info.daylightMinutes in 870..900)
    }

    @Test
    fun `sunrise relative scheduler subtracts offset and respects repeat days`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val settings = UttoSettings(
            isAlarmActive = true,
            alarmMode = AlarmMode.SUNRISE_RELATIVE,
            sunriseOffsetMinutes = 45,
            repeatDays = setOf(DayOfWeek.WEDNESDAY),
            latitude = 37.7749,
            longitude = -122.4194
        )
        // Monday evening -> next active day must be Wednesday
        val mondayEvening = ZonedDateTime.of(2026, 6, 15, 21, 0, 0, 0, zone)
        val next = UttoAlarmScheduler.computeNextAlarm(settings, mondayEvening)
        assertNotNull(next)
        assertEquals(DayOfWeek.WEDNESDAY, next!!.triggerDateTime.dayOfWeek)
        assertEquals(
            next.sunriseForThatDay.minusMinutes(45).withSecond(0),
            next.triggerDateTime.toLocalTime()
        )
    }

    @Test
    fun `manual mode one-time alarm picks tomorrow if today already passed`() {
        val zone = ZoneId.of("UTC")
        val settings = UttoSettings(
            isAlarmActive = true,
            alarmMode = AlarmMode.MANUAL,
            manualHour = 6,
            manualMinute = 15,
            repeatDays = emptySet()
        )
        val lateMorning = ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, zone)
        val next = UttoAlarmScheduler.computeNextAlarm(settings, lateMorning)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 16), next!!.triggerDateTime.toLocalDate())
        assertEquals(LocalTime.of(6, 15), next.triggerDateTime.toLocalTime())
    }
}
