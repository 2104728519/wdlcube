plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appName = "cube"
val appVersionName = "2.5"
val appVersionCode = 9

base {
    archivesName.set("${appName}_v$appVersionName")
}

android {
    namespace = "com.example.cubesolver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cubesolver"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        manifestPlaceholders["app_label"] = appName

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++14")
            }
        }
        // 限制特定架构以精简包体积，仅保留主流 ARM 架构
        ndk {
            abiFilters.addAll(setOf("arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["app_label"] = appName
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            manifestPlaceholders["app_label"] = "${appName}-Debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}


androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val outputImpl = output as? com.android.build.api.variant.impl.VariantOutputImpl
            val originalName = outputImpl?.outputFileName?.get()
            if (originalName != null && originalName.endsWith(".apk")) {

                outputImpl.outputFileName.set(originalName.substringBeforeLast(".") + ".APK")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.concurrent.futures)


    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
