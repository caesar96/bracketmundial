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

    @Query("UPDATE teams SET wins = wins + 1, matchTime = NULL WHERE position = :position")
    suspend fun advance(position: Int)

    @Query("UPDATE teams SET eliminated = 1, matchTime = NULL WHERE position = :position")
    suspend fun eliminate(position: Int)

    @Query("UPDATE teams SET colorArgb = :color WHERE position = :position")
    suspend fun setColor(position: Int, color: Long)

    @Query("UPDATE teams SET wins = wins - 1 WHERE position = :position AND wins > 0")
    suspend fun retreat(position: Int)

    @Query("UPDATE teams SET eliminated = 0 WHERE position = :position")
    suspend fun revive(position: Int)
}
