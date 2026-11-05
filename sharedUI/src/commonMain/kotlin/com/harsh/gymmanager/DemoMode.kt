package com.harsh.gymmanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.harsh.gymmanager.domain.demo.DEMO_PASSWORD
import com.harsh.gymmanager.domain.demo.DemoDataUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Showing the app with sample data. Demo data lives in its own database file, so the gym's real
 * records are never opened for writing while it runs. It isn't kept: leaving demo mode, or
 * starting the app again, deletes it, and the next demo is built fresh around that day.
 *
 * [createDemo] builds dependencies on the demo file (sharing the real settings and files);
 * [deleteDemoFile] removes that file.
 */
class DemoMode(
    private val createDemo: () -> DefaultAppDependencies,
    private val deleteDemoFile: () -> Unit,
) {
    /** The demo's dependencies while it runs; null means the real data is showing. */
    var active: DefaultAppDependencies? by mutableStateOf(null)
        private set

    private val switching = Mutex()

    init {
        // A demo left running when the app was closed is gone; the app always opens on real data.
        deleteDemoFile()
    }

    fun isPassword(text: String): Boolean = text == DEMO_PASSWORD

    /** Builds the demo data and switches to it. */
    suspend fun start(): Result<Unit> = switching.withLock {
        runCatching {
            if (active != null) return@runCatching
            deleteDemoFile()
            val demo = createDemo()
            DemoDataUseCase(demo.memberUseCases, demo.planUseCases, demo.membershipUseCases, demo.paymentUseCases, Clock.System, TimeZone.currentSystemDefault())
                .seed()
                .onFailure {
                    demo.close()
                    deleteDemoFile()
                }
                .getOrThrow()
            active = demo
        }
    }

    /** Back to the real data. The screens close and delete the demo once they've let go of it; see [release]. */
    fun exit() {
        active = null
    }

    /** Closes and deletes a demo after the screens using it are gone, so no query is cut off. */
    fun release(demo: DefaultAppDependencies) {
        demo.close()
        deleteDemoFile()
    }
}

/** The app's demo switch; null where there's no demo, such as previews. */
val LocalDemoMode = staticCompositionLocalOf<DemoMode?> { null }
