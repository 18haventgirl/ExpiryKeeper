plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.expirykeeper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.expirykeeper"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-M1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// room-migration 2.8.5 的 SchemaBundle 序列化器编译自 kotlinx-serialization 1.8.1，
// 但 androidTest 运行时被 strictly 1.7.3 BOM 锁住 core，导致 core/json 版本分裂抛
// AbstractMethodError。迁移测试仅跑在 androidTest classpath，故只在此强制对齐 1.8.1。
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    // 仅测试 JVM 补 org.json（运行时用 Android 内置）
    testImplementation("org.json:json:20240303")

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
