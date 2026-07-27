package com.gerwinkuijntjes.hours.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HoursDao {

    // ---- clients ----

    @Query("SELECT * FROM clients ORDER BY sortOrder, name")
    fun clients(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY sortOrder, name")
    suspend fun clientsNow(): List<Client>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClient(client: Client)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClients(clients: List<Client>)

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    @Query("SELECT COUNT(*) FROM clients")
    suspend fun clientCount(): Int

    // ---- visits ----

    @Query("SELECT * FROM visits WHERE date = :date")
    fun visitsOn(date: String): Flow<List<Visit>>

    @Query("SELECT * FROM visits WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun visitsBetween(from: String, to: String): Flow<List<Visit>>

    @Query("SELECT * FROM visits ORDER BY date")
    fun allVisits(): Flow<List<Visit>>

    @Query("SELECT * FROM visits ORDER BY date")
    suspend fun allVisitsNow(): List<Visit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVisit(visit: Visit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVisits(visits: List<Visit>)

    @Update
    suspend fun updateVisit(visit: Visit)

    @Delete
    suspend fun deleteVisit(visit: Visit)

    @Query("DELETE FROM visits")
    suspend fun clearVisits()

    @Query("DELETE FROM clients")
    suspend fun clearClients()
}
