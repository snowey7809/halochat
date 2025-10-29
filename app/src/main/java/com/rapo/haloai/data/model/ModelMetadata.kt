package com.rapo.haloai.data.model

data class ModelMetadata(
    val contextSize: Int,
    val chatTemplate: String,
    val architecture: String,
    val valid: Boolean
)
