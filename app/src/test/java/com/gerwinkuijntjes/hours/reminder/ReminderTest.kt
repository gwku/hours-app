package com.gerwinkuijntjes.hours.reminder

import com.gerwinkuijntjes.hours.data.Client
import com.gerwinkuijntjes.hours.data.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {

    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = monday.plusDays(1)
    private val saturday = monday.plusDays(5)

    private fun client(id: String, days: String) =
        Client(id, id.replaceFirstChar { it.uppercase() }, 15.0, 3.0, days, 0.0, 0L, 0)

    private fun visit(clientId: String, date: LocalDate) =
        Visit("v-$clientId", date.toString(), clientId, 3.0, 45.0, 0.0, 15.0)

    @Test
    fun `nothing to forget without clients`() {
        assertNull(ReminderDue.on(monday, emptyList(), emptyList()))
    }

    @Test
    fun `lists regular clients that are still open`() {
        val clients = listOf(client("jansen", "1"), client("bakker", "1,3"), client("smit", "2"))
        val due = ReminderDue.on(monday, clients, listOf(visit("bakker", monday)))
        assertEquals(ReminderDue.Clients(listOf("Jansen")), due)
    }

    @Test
    fun `quiet once every regular client is recorded`() {
        val clients = listOf(client("jansen", "1"))
        assertNull(ReminderDue.on(monday, clients, listOf(visit("jansen", monday))))
    }

    @Test
    fun `quiet on a day nobody has as a regular day`() {
        val clients = listOf(client("jansen", "1"))
        assertNull(ReminderDue.on(tuesday, clients, emptyList()))
    }

    @Test
    fun `without regular days falls back to weekdays with nothing recorded`() {
        val clients = listOf(client("jansen", ""))
        assertEquals(ReminderDue.NothingRecorded, ReminderDue.on(monday, clients, emptyList()))
        assertNull(ReminderDue.on(monday, clients, listOf(visit("jansen", monday))))
        assertNull(ReminderDue.on(saturday, clients, emptyList()))
    }

    @Test
    fun `next trigger is today while the time is still ahead, otherwise tomorrow`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val six = LocalTime.of(18, 0)
        val afternoon = ZonedDateTime.of(monday, LocalTime.of(14, 30), zone)
        val evening = ZonedDateTime.of(monday, LocalTime.of(18, 0, 5), zone)

        assertEquals(ZonedDateTime.of(monday, six, zone), Reminders.nextTrigger(six, afternoon))
        assertEquals(ZonedDateTime.of(tuesday, six, zone), Reminders.nextTrigger(six, evening))
    }
}
