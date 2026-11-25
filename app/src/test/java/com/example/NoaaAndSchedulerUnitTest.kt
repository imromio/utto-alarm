package com.example

import com.example.alarm.UttoAlarmScheduler
import com.example.data.AlarmMode
import com.example.data.UttoSettings
import com.example.solar.NoaaSolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NoaaAndSchedulerUnitTest {

    @Test
    fun noaaSolarCalculator_sanFranciscoSummerSolstice_isAccurate() {
        val zone = ZoneId.of("America/Los_Angeles")
        val date = LocalDate.of(2026, 6, 21)
        val solar = NoaaSolarCalculator.calculateDay(
            date = date,
            latitude = 37.7749,
            longitude = -122.4194,
            zoneId = zone
        )
        // San Francisco summer solstice sunrise ~5:48 AM PDT, sunset ~8:35 PM PDT
        assertTrue("Sunrise should be between 5:35 and 6:00 AM, was ${solar.sunrise}",
            solar.sunrise.isAfter(LocalTime.of(5, 35)) && solar.sunrise.isBefore(LocalTime.of(6, 0))
        )
        assertTrue("Sunset should be between 8:20 and 8:50 PM, was ${solar.sunset}",
            solar.sunset.isAfter(LocalTime.of(20, 20)) && solar.sunset.isBefore(LocalTime.of(20, 50))
        )
        assertTrue("Daylight should be > 14 hours, was ${solar.daylightMinutes}m", solar.daylightMinutes > 840)
    }

    @Test
    fun noaaSolarCalculator_multiDaySchedule_returnsRequestedDays() {
        val schedule = NoaaSolarCalculator.calculateMultiDaySchedule(
            startDate = LocalDate.of(2026, 3, 20),
            days = 14,
            latitude = 40.7128,
            longitude = -74.0060,
            zoneId = ZoneId.of("America/New_York")
        )
        assertEquals(14, schedule.size)
        // Around vernal equinox in Northern Hemisphere, daylight increases every day
        assertTrue("Daylight should grow near spring equinox", schedule.last().daylightMinutes > schedule.first().daylightMinutes)
    }

    @Test
    fun alarmScheduler_sunriseRelativeMode_respectsOffsetAndRepeatDays() {
        val zone = ZoneId.of("America/Los_Angeles")
        val mondayEvening = ZonedDateTime.of(2026, 11, 23, 21, 0, 0, 0, zone) // Monday
        val settings = UttoSettings(
            isAlarmActive = true,
            alarmMode = AlarmMode.SUNRISE_RELATIVE,
            sunriseOffsetMinutes = 45,
            repeatDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            latitude = 37.7749,
            longitude = -122.4194
        )
        val next = UttoAlarmScheduler.computeNextAlarm(settings, mondayEvening)
        assertNotNull(next)
        assertEquals(DayOfWeek.WEDNESDAY, next!!.triggerDateTime.dayOfWeek)
        assertEquals(
            next.sunriseForThatDay.minusMinutes(45).withSecond(0),
            next.triggerDateTime.toLocalTime()
        )
    }

    @Test
    fun alarmScheduler_manualOneTimeMode_schedulesNextOccurrence() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, zone)
        val settings = UttoSettings(
            isAlarmActive = true,
            alarmMode = AlarmMode.MANUAL,
            manualHour = 6,
            manualMinute = 30,
            repeatDays = emptySet() // One-time alarm
        )
        val next = UttoAlarmScheduler.computeNextAlarm(settings, now)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 11, 24), next!!.triggerDateTime.toLocalDate())
        assertEquals(LocalTime.of(6, 30), next.triggerDateTime.toLocalTime())
    }
}
