package com.destinyai.astrology.domain.model

data class User(
    val email: String,
    val isGuestEmail: Boolean,
    val name: String? = null,
    val googleId: String? = null,
    val appleId: String? = null,
    val isPremium: Boolean = false,
)
