package io.earlisreal.ejournal

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform