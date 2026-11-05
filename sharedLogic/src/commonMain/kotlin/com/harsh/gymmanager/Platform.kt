package com.harsh.gymmanager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform