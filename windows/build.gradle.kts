import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.2"
}

// The release tag (v1.2.3) decides the version, exactly like the Android release workflow.
val appVersion: String =
    (findProperty("appVersion") as String?)?.removePrefix("v")?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) }
        ?: "1.1.0"
val debugBuild: Boolean = (findProperty("debugBuild") as String?)?.toBoolean() ?: false

group = "com.sabifox.vpn"
version = appVersion

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-Xexpect-actual-classes",
        )
    }
}

val generateBuildConfig by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildconfig")
    val version = appVersion
    val debug = debugBuild
    inputs.property("version", version)
    inputs.property("debug", debug)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.resolve("com/sabifox/vpn").apply { mkdirs() }
        dir.resolve("BuildConfig.kt").writeText(
            """
            package com.sabifox.vpn

            object BuildConfig {
                const val DEBUG: Boolean = $debug
                const val APPLICATION_ID: String = "com.sabifox.vpn"
                const val VERSION_NAME: String = "$version"
                const val BUILD_TYPE: String = "${if (debug) "debug" else "release"}"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateBuildConfig)
}

dependencies {
    implementation(compose.desktop.windows_x64)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20250517")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")

    testImplementation(kotlin("test"))
}

compose.resources {
    packageOfResClass = "com.sabifox.vpn.resources"
    publicResClass = false
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.sabifox.vpn.MainKt"
        jvmArgs += listOf(
            "-Xms64m",
            "-Xmx512m",
            "-XX:+UseSerialGC",
            "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
        )
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "SABI-FOX"
            packageVersion = appVersion
            description = "SABI-FOX VPN"
            vendor = "SABI-FOX"
            copyright = "SABI-FOX contributors"
            licenseFile.set(project.file("LICENSE"))
            // sabifox-core.exe and the sign-in window are placed here by CI (see README).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("native"))
            modules(
                "java.desktop",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.sql",
                "jdk.accessibility",
                "jdk.charsets",
                "jdk.crypto.ec",
                "jdk.localedata",
                "jdk.unsupported",
                "jdk.zipfs",
            )
            windows {
                iconFile.set(project.file("packaging/sabifox.ico"))
                console = false
                upgradeUuid = "8F3C6E52-1E7B-4C0F-9B7A-5ABF0F0C5A11"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
