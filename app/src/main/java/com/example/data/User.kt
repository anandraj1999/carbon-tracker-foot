package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String, // Treat lowercase email as unique identifier
    val name: String,
    val passwordHash: String, // Securely salted SHA-256 hash
    val salt: String, // Unique random salt generated at registration
    val lastLoginTime: Long = System.currentTimeMillis()
)
