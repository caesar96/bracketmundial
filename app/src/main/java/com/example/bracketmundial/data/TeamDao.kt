package com.example.bracketmundial.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY position")
    fun observeAll(): Flow<List<TeamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(team: TeamEntity)

    @Delete
    suspend fun delete(team: TeamEntity)

    @Query("UPDATE teams SET status = :status, hora = NULL WHERE position = :position")
    suspend fun setStatus(position: Int, status: String)

    @Query("UPDATE teams SET colorArgb = :color WHERE position = :position")
    suspend fun setColor(position: Int, color: Long)
}
