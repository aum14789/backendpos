package com.sunpos.backend.domain.businessday

import org.springframework.stereotype.Component
import java.time.*

@Component
class BusinessDayClock {
    fun instant(): Instant = Instant.now()
    fun zonedDateTime(zoneId: ZoneId): ZonedDateTime = ZonedDateTime.now(zoneId)
}

@Component
class BusinessDayResolver(private val clock: BusinessDayClock) {
    /**
     * Resolves the business date for a branch at the given instant.
      If the current time is before the closing time limit on the next calendar day,
     * it still resolves to the previous calendar day's business day.
     * For example, with closingTimeSetting = "02:00", 2026-08-27 at 01:30 resolves to 2026-08-26.
     */
    fun resolveBusinessDate(instant: Instant, zoneId: ZoneId, closingTimeSetting: String): LocalDate {
        val zonedDateTime = instant.atZone(zoneId)
        val timeLimit = try {
            LocalTime.parse(closingTimeSetting)
        } catch (e: Exception) {
            LocalTime.of(2, 0)
        }
        
        val localTime = zonedDateTime.toLocalTime()
        return if (localTime.isBefore(timeLimit)) {
            zonedDateTime.toLocalDate().minusDays(1)
        } else {
            zonedDateTime.toLocalDate()
        }
    }
}
