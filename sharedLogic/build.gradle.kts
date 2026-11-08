import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom)
    alias(libs.plugins.kotlinSerialization)
}

/*
 * Which Supabase project the app talks to. Set supabase.url and supabase.publishableKey in local.properties
 * to use a hosted project; without them the app uses local Supabase (`supabase start`), whose key is the
 * same fixed development key on every machine.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val supabaseUrl = localProperties.getProperty("supabase.url") ?: "http://127.0.0.1:54321"
val supabasePublishableKey = localProperties.getProperty("supabase.publishableKey")
    ?: "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"

/*
 * Google sign-in's OAuth client IDs from Google Cloud: google.webClientId (Android asks Google for a token
 * meant for it) and google.iosClientId. Without them the app doesn't offer Google sign-in.
 */
val googleWebClientId = localProperties.getProperty("google.webClientId").orEmpty()
val googleIosClientId = localProperties.getProperty("google.iosClientId").orEmpty()

/*
 * Signing in with a phone number and a texted code. Off for now, since real SMS in India needs a paid
 * provider; signin.phone=true in local.properties brings it back, e.g. to use the test numbers.
 */
val phoneSignIn = localProperties.getProperty("signin.phone")?.toBoolean() ?: false

val generateSupabaseConfig by tasks.registering {
    // Locals rather than the script's own values, which the configuration cache can't store.
    val url = supabaseUrl
    val publishableKey = supabasePublishableKey
    val webClientId = googleWebClientId
    val iosClientId = googleIosClientId
    val phone = phoneSignIn
    val outputDir = layout.buildDirectory.dir("generated/supabaseConfig/kotlin")
    inputs.property("url", url)
    inputs.property("publishableKey", publishableKey)
    inputs.property("webClientId", webClientId)
    inputs.property("iosClientId", iosClientId)
    inputs.property("phoneSignIn", phone)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/harsh/gymmanager/data/remote/SupabaseBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.harsh.gymmanager.data.remote
            |
            |// Generated from local.properties by sharedLogic/build.gradle.kts; don't edit.
            |internal object SupabaseBuildConfig {
            |    const val URL = "$url"
            |    const val PUBLISHABLE_KEY = "$publishableKey"
            |}
            |
            |/** Google sign-in's OAuth client IDs; blank where Google sign-in isn't set up. */
            |object GoogleSignInConfig {
            |    const val WEB_CLIENT_ID = "$webClientId"
            |    const val IOS_CLIENT_ID = "$iosClientId"
            |}
            |
            |/** Which ways of signing in this build offers besides Google and Apple. */
            |object SignInConfig {
            |    const val PHONE = $phone
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    compilerOptions {
        // Required for the Room database constructor.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
        }
    }
    android {
       namespace = "com.harsh.gymmanager.sharedLogic"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            api(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.serialization.json)
            api(project(":whatsapp-cloud"))
            api(project(":gym-templates"))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)
        }
        commonMain {
            kotlin.srcDir(generateSupabaseConfig)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}