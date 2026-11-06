package com.harsh.gymmanager.data.remote

/** The simulator shares the Mac's network, so 127.0.0.1 works as it is. */
internal actual fun reachableFromDevice(url: String): String = url
