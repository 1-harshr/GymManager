package com.harsh.gymmanager.data.remote

/** The emulator sees the Mac as 10.0.2.2. A real phone needs the Mac's network address in local.properties. */
internal actual fun reachableFromDevice(url: String): String =
    url.replace("://127.0.0.1", "://10.0.2.2").replace("://localhost", "://10.0.2.2")
