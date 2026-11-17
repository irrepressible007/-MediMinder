package com.example.mediminder.data

import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.AppointmentStatus
import com.example.mediminder.data.local.entity.Doctor
import com.example.mediminder.reminders.ReminderEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Doctor visits and the doctors/chambers the user goes to. */
@Singleton
class AppointmentRepository @Inject constructor(
    db: MediMinderDatabase,
    private val engine: ReminderEngine,
) {
    private val appointmentDao = db.appointmentDao()
    private val doctorDao = db.doctorDao()

    fun observeAppointments(): Flow<List<Appointment>> = appointmentDao.observeAll()

    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    suspend fun getAppointment(id: Long): Appointment? = appointmentDao.getById(id)

    suspend fun getDoctor(id: Long): Doctor? = doctorDao.getById(id)

    suspend fun save(appointment: Appointment): Long {
        val id = if (appointment.id == 0L) appointmentDao.insert(appointment) else {
            appointmentDao.update(appointment)
            appointment.id
        }
        engine.rescheduleAppointment(id)
        return id
    }

    suspend fun setStatus(id: Long, status: AppointmentStatus) {
        val appointment = appointmentDao.getById(id) ?: return
        appointmentDao.update(appointment.copy(status = status))
        if (status == AppointmentStatus.UPCOMING) engine.rescheduleAppointment(id) else engine.cancelAppointment(id)
    }

    suspend fun delete(id: Long) {
        val appointment = appointmentDao.getById(id) ?: return
        engine.cancelAppointment(id)
        appointmentDao.delete(appointment)
    }

    suspend fun saveDoctor(doctor: Doctor): Long =
        if (doctor.id == 0L) doctorDao.insert(doctor) else {
            doctorDao.update(doctor)
            doctor.id
        }

    suspend fun deleteDoctor(doctor: Doctor) = doctorDao.delete(doctor)
}
