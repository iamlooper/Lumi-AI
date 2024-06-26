plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

android {
    namespace = "com.looper.vic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.looper.vic"
        minSdk = 27
        targetSdk = 34
        versionCode = 37
        versionName = "2.0.0"

        vectorDrawables.useSupportLibrary = true
    }
    
    buildFeatures {
        buildConfig = true
    }    
    
    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }    
    
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.preference)
    
    implementation(libs.google.material)

    implementation(libs.noties.markwon.core)
    implementation(libs.noties.markwon.linkify)
    implementation(libs.noties.markwon.ext.latex)
    implementation(libs.noties.markwon.ext.strikethrough)
    implementation(libs.noties.markwon.ext.tables)
    implementation(libs.noties.markwon.ext.tasklist)
    implementation(libs.noties.markwon.image)
    implementation(libs.noties.markwon.image.glide)

    implementation(libs.saket.better.link.movement.method)

    implementation(libs.squareup.okhttp)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(libs.looper.utils.android.support)

    implementation(project(":loading-dots"))
}