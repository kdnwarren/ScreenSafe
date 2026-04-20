package com.example.screensafe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DailyUsageEntity)

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDate(dateKey: String): DailyUsageEntity?

    @Query("SELECT * FROM daily_usage ORDER BY dateKey DESC LIMIT 7")
    suspend fun getLast7Days(): List<DailyUsageEntity>

    @Query("DELETE FROM daily_usage")
    suspend fun clearAll()
}
