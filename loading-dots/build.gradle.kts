plugins {
    id("com.android.library")
    id("kotlin-android")     
}

android {
    namespace = "com.eyalbira.loadingdots"
    compileSdk = 34
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 21
    }

    kotlinOptions {
        jvmTarget = "17"
    }    
               
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
}