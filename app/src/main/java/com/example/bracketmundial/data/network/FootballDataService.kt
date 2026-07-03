package com.example.bracketmundial.data.network

import retrofit2.http.GET

interface FootballDataService {
    @GET("competitions/WC/matches")
    suspend fun matches(): FootballDataResponse
}
