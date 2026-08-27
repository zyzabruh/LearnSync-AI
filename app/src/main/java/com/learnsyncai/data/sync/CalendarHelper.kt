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
                    val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    val isPrimaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                    val accessCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

                    val id = cursor.getLong(idCol)
                    val isPrimary = if (isPrimaryCol != -1) cursor.getInt(isPrimaryCol) == 1 else false
                    val accessLevel = if (accessCol != -1) cursor.getInt(accessCol) else CalendarContract.Calendars.CAL_ACCESS_OWNER

                    // Ensure write permission on the calendar
                    if (accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        if (isPrimary) {
                            return id
                        }
                        if (fallbackId == null) {
                            fallbackId = id
                        }
                    }
                }
                return fallbackId
            }
        } catch (_: Exception) {
            // Fallback or permission denial
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Sync review sessions to system calendar.
     * Uses androidEventId stored in Room to update existing events or create new ones without duplicates.
     */
    suspend fun syncReviewsToCalendar(
        context: Context,
        courses: List<Course>,
        dueCards: List<Flashcard>,
        calendarEventDao: CalendarEventDao? = null
    ): Int {
        if (!hasCalendarPermission(context)) return 0
        val calId = getPrimaryCalendarId(context) ?: return 0

        val coursesMap = courses.associateBy { it.id }
        val cardsByDateAndCourse = dueCards.groupBy { card ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = card.dueDate }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            Pair(cal.timeInMillis, card.courseId)
        }

        var eventsProcessed = 0

        for ((key, cards) in cardsByDateAndCourse) {
            val (eventTime, courseId) = key
            val courseTitle = coursesMap[courseId]?.title ?: "Général"
            val title = "LearnSync : Révision $courseTitle (${cards.size} cartes)"
            val description = "Session de révision espacée planifiée par LearnSync AI pour le cours $courseTitle (${cards.size} cartes dues)."

            val existingRoomEvent = calendarEventDao?.getEventForCourseAndDate(courseId, eventTime)

            if (existingRoomEvent != null && existingRoomEvent.androidEventId != null) {
                // Event known in Room -> check & update in system calendar by androidEventId
                val updated = updateCalendarEvent(
                    context = context,
                    androidEventId = existingRoomEvent.androidEventId,
                    title = title,
                    description = description,
                    startTime = eventTime,
                    endTime = eventTime + (30 * 60 * 1000)
                )
                if (updated) {
                    calendarEventDao.insertEvent(
                        existingRoomEvent.copy(
                            title = title,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    eventsProcessed++
                } else {
                    // Stale ID, recreate
                    val eventUri = insertCalendarEvent(
                        context = context,
                        calendarId = calId,
                        title = title,
                        description = description,
                        startTime = eventTime,
                        endTime = eventTime + (30 * 60 * 1000)
                    )
                    if (eventUri != null) {
                        val androidId = ContentUris.parseId(eventUri)
                        calendarEventDao.insertEvent(
                            existingRoomEvent.copy(
                                title = title,
                                androidEventId = androidId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        eventsProcessed++
                    }
                }
            } else {
                // New event
                val eventUri = insertCalendarEvent(
                    context = context,
                    calendarId = calId,
                    title = title,
                    description = description,
                    startTime = eventTime,
                    endTime = eventTime + (30 * 60 * 1000) // 30 minutes
                )
                if (eventUri != null) {
                    val androidId = ContentUris.parseId(eventUri)
                    calendarEventDao?.insertEvent(
                        CalendarEventEntity(
                            id = UUID.randomUUID().toString(),
                            courseId = courseId,
                            title = title,
                            scheduledDate = eventTime,
                            androidEventId = androidId,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    eventsProcessed++
                }
            }
        }

        return eventsProcessed
    }

    fun updateCalendarEvent(
        context: Context,
        androidEventId: Long,
        title: String,
        description: String,
        startTime: Long,
        endTime: Long
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
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun deleteCalendarEvent(context: Context, androidEventId: Long): Boolean {
        if (!hasCalendarPermission(context)) return false
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, androidEventId)
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
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
        endTime: Long
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            if (uri != null) {
                val eventId = ContentUris.parseId(uri)
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    put(CalendarContract.Reminders.MINUTES, 15) // 15 min before
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }
}
