package com.example.bracketmundial.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bracketmundial.INITIAL_TEAMS
import kotlinx.coroutines.flow.first

@Database(entities = [TeamEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bracket.db"
                ).build().also { INSTANCE = it }
            }
    }
}

/** Siembra los 32 equipos iniciales si la tabla está vacía (primer arranque). */
suspend fun TeamDao.seedIfEmpty() {
    if (observeAll().first().isEmpty()) {
        INITIAL_TEAMS.forEach { upsert(it.toEntity()) }
    }
}
