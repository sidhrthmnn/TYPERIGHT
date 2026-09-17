package com.example

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for the NVIDIA Nemotron AI API.
 * Interacts with NVIDIA NIM endpoint at https://integrate.api.nvidia.com/v1/
 */
interface NvidiaNemotronApiService {

    /**
     * Creates a chat completion using NVIDIA Nemotron models.
     */
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: NemotronChatRequest
    ): Response<NemotronChatResponse>
}
