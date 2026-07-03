package com.example.bracketmundial.data

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.bracketmundial.Team

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val position: Int,
    val name: String,
    val flag: String,
    val colorArgb: Long?,
    val wins: Int,
    val eliminated: Boolean,
    val matchTime: String?,
    val countryKey: String?,
)

fun TeamEntity.toTeam(): Team = Team(
    n = name,
    f = flag,
    c = colorArgb?.let { Color(it.toULong()) },
    wins = wins,
    eliminated = eliminated,
    matchTime = matchTime,
    position = position,
    countryKey = countryKey,
)

fun Team.toEntity(): TeamEntity = TeamEntity(
    position = position,
    name = n,
    flag = f,
    colorArgb = c?.value?.toLong(),
    wins = wins,
    eliminated = eliminated,
    matchTime = matchTime,
    countryKey = countryKey,
)
