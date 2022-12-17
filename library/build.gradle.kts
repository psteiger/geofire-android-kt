import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

android {
    namespace = "com.firebase.geofire"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        targetSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-Xexplicit-api=strict",
            "-Xcontext-receivers",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

tasks.withType(Test::class).configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.PASSED, TestLogEvent.FAILED)
    }
}

dependencies {
    api("com.google.firebase:firebase-database:20.1.0")
    implementation("com.github.psteiger:firebase-database-ktx:0.2.9")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("androidx.annotation:annotation:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.4")
    testImplementation("androidx.test.espresso:espresso-core:3.5.0")
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            register("release", MavenPublication::class) {
                from(components["release"])
                artifactId = project.name
            }
        }
    }
}