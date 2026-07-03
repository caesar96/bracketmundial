package com.example.bracketmundial.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bracketmundial.Team
import kotlinx.coroutines.flow.first

@Database(entities = [TeamEntity::class], version = 4, exportSchema = false)
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
                )
                    // Personal app with no existing users to migrate: instead of writing
                    // a real migration, the table is recreated and reseeded from initialTeams().
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/** Seeds the 32 initial teams if the table is empty (first launch). */
suspend fun TeamDao.seedIfEmpty(teams: List<Team>) {
    if (observeAll().first().isEmpty()) {
        teams.forEach { upsert(it.toEntity()) }
    }
}
