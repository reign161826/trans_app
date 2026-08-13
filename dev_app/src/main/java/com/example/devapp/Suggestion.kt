package com.example.devapp

data class Suggestion(
    val id: String = "",
    val word: String = "",
    val sourceLang: String = "",
    val targetLang: String = "",
    val userTranslation: String = "",
    val translation1: String = "",
    val translation2: String = "",
    val description: String = "",
    val status: String = "pending",
    val timestamp: Long = 0
)