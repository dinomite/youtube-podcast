package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String)
