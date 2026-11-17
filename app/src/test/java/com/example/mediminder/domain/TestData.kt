package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.local.entity.Schedule
import java.time.LocalDate
import java.time.ZoneId

/** Small builders so tests read like the scenario they check. */
object TestData {
    val zone: ZoneId = ZoneId.systemDefault()

    fun medication(
        id: Long = 1,
        name: String = "Napa",
        generic: String? = "Paracetamol",
        stock: Double = 0.0,
        trackStock: Boolean = false,
        start: LocalDate? = null,
        end: LocalDate? = null,
        asNeeded: Boolean = false,
        archived: Boolean = false,
        threshold: Int = 5,
        createdOn: LocalDate = LocalDate.of(2026, 1, 1),
    ) = Medication(
        id = id, name = name, genericName = generic, stock = stock, trackStock = trackStock,
        startDate = start?.toEpochDay(), endDate = end?.toEpochDay(), isAsNeeded = asNeeded, archived = archived,
        refillThresholdDays = threshold,
        createdAt = createdOn.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    fun schedule(
        id: Long = 10,
        medicationId: Long = 1,
        hour: Int = 8,
        minute: Int = 0,
        days: Int = DaysOfWeek.EVERY_DAY,
        quantity: Double = 1.0,
        alert: AlertType = AlertType.REMINDER,
        enabled: Boolean = true,
    ) = Schedule(id = id, medicationId = medicationId, minuteOfDay = hour * 60 + minute, daysMask = days, quantity = quantity, alertType = alert, enabled = enabled)

    fun item(medication: Medication, vararg schedules: Schedule) = MedicationWithSchedules(medication, schedules.toList())
}
