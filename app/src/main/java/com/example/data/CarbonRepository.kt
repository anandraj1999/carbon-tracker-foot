package com.example.data

import kotlinx.coroutines.flow.Flow

class CarbonRepository(
    private val carbonDao: CarbonDao,
    private val userDao: UserDao
) {
    val allActivities: Flow<List<CarbonActivity>> = carbonDao.getAllActivities()

    fun getActivitiesForUser(userEmail: String): Flow<List<CarbonActivity>> {
        return carbonDao.getActivitiesForUser(userEmail)
    }

    fun getActivitiesInTimeRange(startTime: Long, endTime: Long): Flow<List<CarbonActivity>> {
        return carbonDao.getActivitiesInTimeRange(startTime, endTime)
    }

    fun getActivitiesInTimeRangeForUser(userEmail: String, startTime: Long, endTime: Long): Flow<List<CarbonActivity>> {
        return carbonDao.getActivitiesInTimeRangeForUser(userEmail, startTime, endTime)
    }

    suspend fun insertActivity(activity: CarbonActivity) {
        carbonDao.insertActivity(activity)
    }

    suspend fun deleteActivityById(id: Int) {
        carbonDao.deleteActivityById(id)
    }

    suspend fun clearAll() {
        carbonDao.clearAll()
    }

    suspend fun clearAllForUser(userEmail: String) {
        carbonDao.clearAllForUser(userEmail)
    }

    // --- User operation delegation ---
    suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)
    }

    suspend fun registerUser(user: User) {
        userDao.registerUser(user)
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
    }
}
