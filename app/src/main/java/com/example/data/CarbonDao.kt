package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CarbonDao {
    @Query("SELECT * FROM carbon_activities ORDER BY timestamp DESC")
    fun getAllActivities(): Flow<List<CarbonActivity>>

    @Query("SELECT * FROM carbon_activities WHERE userEmail = :userEmail ORDER BY timestamp DESC")
    fun getActivitiesForUser(userEmail: String): Flow<List<CarbonActivity>>

    @Query("SELECT * FROM carbon_activities WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getActivitiesInTimeRange(startTime: Long, endTime: Long): Flow<List<CarbonActivity>>

    @Query("SELECT * FROM carbon_activities WHERE userEmail = :userEmail AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getActivitiesInTimeRangeForUser(userEmail: String, startTime: Long, endTime: Long): Flow<List<CarbonActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: CarbonActivity)

    @Delete
    suspend fun deleteActivity(activity: CarbonActivity)

    @Query("DELETE FROM carbon_activities WHERE id = :id")
    suspend fun deleteActivityById(id: Int)

    @Query("DELETE FROM carbon_activities WHERE userEmail = :userEmail")
    suspend fun clearAllForUser(userEmail: String)

    @Query("DELETE FROM carbon_activities")
    suspend fun clearAll()
}
