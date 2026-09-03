package com.learnsyncai.data.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.learnsyncai.data.database.CalendarEventDao
import com.learnsyncai.data.database.CalendarEventEntity
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.UserPreferences
import com.learnsyncai.domain.usecase.SpacedRepetition
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

object CalendarHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    fun getPrimaryCalendarId(context: Context): Long? {
        if (!hasCalendarPermission(context)) return null

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            if (cursor != null) {
                var fallbackId: Long? = null
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                    val isPrimaryColumn = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                    val accessColumn = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                    val isPrimary = isPrimaryColumn != -1 && cursor.getInt(isPrimaryColumn) == 1
                    val accessLevel = if (accessColumn != -1) {
                        cursor.getInt(accessColumn)
                    } else {
                        CalendarContract.Calendars.CAL_ACCESS_OWNER
                    }

                    if (accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        if (isPrimary) return id
                        if (fallbackId == null) fallbackId = id
                    }
                }
                return fallbackId
            }
        } catch (_: Exception) {
            // Permission denial or provider failure.
        } finally {
            cursor?.close()
        }
        return null
    }

    private data class ScheduleKey(val courseId: String, val day: Long)

    private data class PlannedBlock(
        val key: ScheduleKey,
        val cardCount: Int,
        val courseTitle: String
    )

    /**
     * Synchronize actual and forecast review load into Android Calendar.
     * One event is created per course and local day, with stale managed events removed.
     */
    suspend fun syncReviewsToCalendar(
        context: Context,
        courses: List<Course>,
        allCards: List<Flashcard>,
        preferences: UserPreferences = UserPreferences.DEFAULT,
        calendarEventDao: CalendarEventDao? = null,
        now: Long = System.currentTimeMillis()
    ): Int {
        if (!hasCalendarPermission(context)) return 0
        val calendarId = getPrimaryCalendarId(context) ?: return 0
        val horizonDays = preferences.calendarHorizonDays.coerceIn(1, 365)
        val horizonEnd = now + DAY_MS * horizonDays
        val today = dayStart(now)
        val coursesById = courses.associateBy { it.id }
        val plannedByKey = linkedMapOf<ScheduleKey, MutableSet<String>>()

        allCards.forEach { card ->
            val actualDay = dayStart(card.dueDate.coerceAtLeast(today))
            if (actualDay <= horizonEnd) {
                plannedByKey.getOrPut(ScheduleKey(card.courseId, actualDay)) { linkedSetOf() }.add(card.id)
            }
            SpacedRepetition.forecastSchedule(card, horizonDays, now).forEach { forecastDate ->
                val forecastDay = dayStart(forecastDate)
                if (forecastDay in today..horizonEnd) {
                    plannedByKey.getOrPut(ScheduleKey(card.courseId, forecastDay)) { linkedSetOf() }.add(card.id)
                }
            }
        }

        val startTime = parseTime(preferences.calendarStartTime.ifBlank { preferences.reminderTime })
        val desiredBlocks = plannedByKey.map { (key, cardIds) ->
            PlannedBlock(key, cardIds.size, coursesById[key.courseId]?.title ?: "Général")
        }
        val existingEvents = calendarEventDao?.getAllCalendarEventsOnce().orEmpty()
        val existingByKey = existingEvents.associateBy { ScheduleKey(it.courseId, dayStart(it.scheduledDate)) }
        val desiredKeys = desiredBlocks.mapTo(hashSetOf()) { it.key }
        var eventsProcessed = 0

        for (block in desiredBlocks) {
            val eventTime = withTime(block.key.day, startTime.first, startTime.second)
            val title = "LearnSync : Révision ${block.courseTitle} (${block.cardCount} cartes)"
            val description = "Charge FSRS prévue pour ${block.courseTitle} : ${block.cardCount} cartes à réviser."
            val existing = existingByKey[block.key]
            val androidId = existing?.androidEventId
            val updated = if (androidId != null) {
                updateCalendarEvent(
                    context = context,
                    androidEventId = androidId,
                    title = title,
                    description = description,
                    startTime = eventTime,
                    endTime = eventTime + preferences.calendarDurationMinutes.coerceIn(5, 240) * MINUTE_MS,
                    reminderMinutes = preferences.calendarReminderMinutes.coerceIn(0, 24 * 60)
                )
            } else {
                false
            }

            val resolvedAndroidId = if (updated) {
                androidId
            } else {
                if (androidId != null) deleteCalendarEvent(context, androidId)
                insertCalendarEvent(
                    context = context,
                    calendarId = calendarId,
                    title = title,
                    description = description,
                    startTime = eventTime,
                    endTime = eventTime + preferences.calendarDurationMinutes.coerceIn(5, 240) * MINUTE_MS,
                    reminderMinutes = preferences.calendarReminderMinutes.coerceIn(0, 24 * 60)
                )?.let(ContentUris::parseId)
            }

            if (resolvedAndroidId != null && calendarEventDao != null) {
                calendarEventDao.insertEvent(
                    (existing ?: CalendarEventEntity(
                        id = UUID.randomUUID().toString(),
                        courseId = block.key.courseId,
                        title = title,
                        scheduledDate = eventTime,
                        androidEventId = resolvedAndroidId,
                        updatedAt = now
                    )).copy(
                        title = title,
                        scheduledDate = eventTime,
                        androidEventId = resolvedAndroidId,
                        updatedAt = now
                    )
                )
                eventsProcessed++
            }
        }

        if (calendarEventDao != null) {
            existingEvents.filter { ScheduleKey(it.courseId, dayStart(it.scheduledDate)) !in desiredKeys }
                .forEach { stale ->
                    stale.androidEventId?.let { deleteCalendarEvent(context, it) }
                    calendarEventDao.deleteEvent(stale.id)
                    eventsProcessed++
                }
        }

        return eventsProcessed
    }

    suspend fun deleteEventsForCourse(
        context: Context,
        calendarEventDao: CalendarEventDao,
        courseId: String
    ): Int {
        val events = calendarEventDao.getEventsForCourseOnce(courseId)
        events.forEach { it.androidEventId?.let { androidId -> deleteCalendarEvent(context, androidId) } }
        calendarEventDao.deleteEventsForCourse(courseId)
        return events.size
    }

    fun updateCalendarEvent(
        context: Context,
        androidEventId: Long,
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        reminderMinutes: Int = 15
    ): Boolean {
        if (!hasCalendarPermission(context)) return false
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, androidEventId)
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes >= 0) 1 else 0)
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                replaceReminder(context, androidEventId, reminderMinutes)
            }
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun deleteCalendarEvent(context: Context, androidEventId: Long): Boolean {
        if (!hasCalendarPermission(context)) return false
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, androidEventId)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun insertCalendarEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        reminderMinutes: Int
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes >= 0) 1 else 0)
            }
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (eventUri != null) {
                replaceReminder(context, ContentUris.parseId(eventUri), reminderMinutes)
            }
            eventUri
        } catch (_: Exception) {
            null
        }
    }

    private fun replaceReminder(context: Context, eventId: Long, reminderMinutes: Int) {
        context.contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
        if (reminderMinutes >= 0) {
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                }
            )
        }
    }

    private fun parseTime(value: String): Pair<Int, Int> {
        val parts = value.split(":")
        val hour = (parts.getOrNull(0)?.toIntOrNull() ?: 8).coerceIn(0, 23)
        val minute = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        return hour to minute
    }

    private fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun withTime(day: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        timeInMillis = day
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MINUTE_MS = 60_000L
}
