package com.bili.bilitv

import kotlinx.serialization.Serializable

@Serializable
data class WbiImg(
    val img_url: String,
    val sub_url: String
)
