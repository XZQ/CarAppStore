plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xzq.appstore.data"
    compileSdk = 36

    fun configValue(name: String): String =
        (project.findProperty(name) as String?)
            ?: System.getenv(name)
            ?: ""

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "consumer-rules.pro",
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "CARAPPSTORE_CATALOG_DEV_URL", "\"${configValue("CARAPPSTORE_CATALOG_DEV_URL")}\"")
        buildConfigField("String", "CARAPPSTORE_CATALOG_TEST_URL", "\"${configValue("CARAPPSTORE_CATALOG_TEST_URL")}\"")
        buildConfigField("String", "CARAPPSTORE_CATALOG_PROD_URL", "\"${configValue("CARAPPSTORE_CATALOG_PROD_URL")}\"")
        buildConfigField("String", "CARAPPSTORE_DOWNLOAD_DEV_BASE_URL", "\"${configValue("CARAPPSTORE_DOWNLOAD_DEV_BASE_URL")}\"")
        buildConfigField("String", "CARAPPSTORE_DOWNLOAD_TEST_BASE_URL", "\"${configValue("CARAPPSTORE_DOWNLOAD_TEST_BASE_URL")}\"")
        buildConfigField("String", "CARAPPSTORE_DOWNLOAD_PROD_BASE_URL", "\"${configValue("CARAPPSTORE_DOWNLOAD_PROD_BASE_URL")}\"")
        buildConfigField("String", "CARAPPSTORE_CATALOG_AUTH_HEADER", "\"${configValue("CARAPPSTORE_CATALOG_AUTH_HEADER")}\"")
        buildConfigField("String", "CARAPPSTORE_CATALOG_AUTH_VALUE", "\"${configValue("CARAPPSTORE_CATALOG_AUTH_VALUE")}\"")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
