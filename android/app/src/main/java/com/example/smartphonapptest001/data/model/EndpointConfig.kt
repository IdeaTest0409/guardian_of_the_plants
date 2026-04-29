package com.example.smartphonapptest001.data.model

data class EndpointConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String?,
    val streamResponses: Boolean,
)
