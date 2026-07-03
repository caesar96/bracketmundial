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
    val status: String,
    val hora: String?,
)

fun TeamEntity.toTeam(): Team = Team(
    n = name,
    f = flag,
    c = colorArgb?.let { Color(it.toULong()) },
    s = status.first(),
    hora = hora,
    position = position,
)

fun Team.toEntity(): TeamEntity = TeamEntity(
    position = position,
    name = n,
    flag = f,
    colorArgb = c?.value?.toLong(),
    status = s.toString(),
    hora = hora,
)
