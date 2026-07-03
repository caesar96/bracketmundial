package com.example.bracketmundial.data.network

import retrofit2.http.GET

interface OpenFootballService {
    // URL absoluta: Retrofit la usa tal cual y no requiere el baseUrl del cliente.
    @GET("https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json")
    suspend fun worldCup2026(): OpenFootballResponse
}
