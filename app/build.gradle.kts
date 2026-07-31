plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun configValue(name: String): String {
    return (project.findProperty(name) as String?)
        ?: System.getenv(name)
        ?: ""
}

val releaseStoreFilePath = configValue("CARAPPSTORE_RELEASE_STORE_FILE")
val releaseStorePassword = configValue("CARAPPSTORE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = configValue("CARAPPSTORE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = configValue("CARAPPSTORE_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }

android {
    namespace = "com.xzq.appstore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xzq.appstore"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "CARAPPSTORE_OEM_VEHICLE_ACTION", "\"${configValue("CARAPPSTORE_OEM_VEHICLE_ACTION")}\"")
        buildConfigField("String", "CARAPPSTORE_OEM_PARKING_EXTRA", "\"${configValue("CARAPPSTORE_OEM_PARKING_EXTRA")}\"")
        buildConfigField("String", "CARAPPSTORE_OEM_POWER_EXTRA", "\"${configValue("CARAPPSTORE_OEM_POWER_EXTRA")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(project(":feature-debug"))
    implementation(project(":feature-installcenter"))
    implementation(project(":feature-upgrade"))
    implementation(project(":feature-downloadmanager"))
    implementation(project(":feature-search"))
    implementation(project(":feature-myapp"))
    implementation(project(":feature-detail"))
    implementation(project(":feature-home"))
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":business"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
