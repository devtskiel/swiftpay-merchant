import java.util.Properties
import org.gradle.api.GradleException

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

fun secretProperty(name: String, defaultValue: String = "MISSING_KEY"): String {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProps.getProperty(name)
        ?: defaultValue
}

fun optionalProperty(name: String): String {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProps.getProperty(name)
        ?: ""
}

// Read a couple of properties at configuration time so we can enforce them for release builds
val APP_SERVER_URL_PROP: String = providers.gradleProperty("APP_SERVER_URL").orNull
    ?: providers.environmentVariable("APP_SERVER_URL").orNull
    ?: localProps.getProperty("APP_SERVER_URL")
    ?: ""
val APP_SERVER_KEY_PROP: String = providers.gradleProperty("APP_SERVER_KEY").orNull
    ?: providers.environmentVariable("APP_SERVER_KEY").orNull
    ?: localProps.getProperty("APP_SERVER_KEY")
    ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val swiftPayAccessKey = optionalProperty("SWIFTPAY_ACCESS_KEY").ifBlank { secretProperty("SWIFTPAY_API_KEY") }
        val swiftPaySecretKey = optionalProperty("SWIFTPAY_SECRET_KEY")
        val swiftPayBaseUrl = optionalProperty("SWIFTPAY_BASE_URL").takeUnless { it.isBlank() } ?: "https://api.pay.live.swiftpay.ph/api"
        val swiftPayMerchantId = optionalProperty("SWIFTPAY_MERCHANT_ID")
        val swiftPayTerminalId = optionalProperty("SWIFTPAY_TERMINAL_ID")
        val resendKey = secretProperty("RESEND_API_KEY")
        val otpSender = optionalProperty("OTP_SENDER_EMAIL").takeUnless { it.isBlank() } ?: "onboarding@resend.dev"
        val swiftPaySuccess = optionalProperty("SWIFTPAY_SUCCESS_REDIRECT_URL").takeUnless { it.isBlank() } ?: "https://api.pay.live.swiftpay.ph/success"
        val swiftPayFailure = optionalProperty("SWIFTPAY_FAILURE_REDIRECT_URL").takeUnless { it.isBlank() } ?: "https://api.pay.live.swiftpay.ph/failure"
        val swiftPayCancel = optionalProperty("SWIFTPAY_CANCEL_REDIRECT_URL").takeUnless { it.isBlank() } ?: "https://api.pay.live.swiftpay.ph/cancel"
        val appServerKey = optionalProperty("APP_SERVER_KEY")

        buildConfigField("String", "SWIFTPAY_ACCESS_KEY", "\"$swiftPayAccessKey\"")
        buildConfigField("String", "SWIFTPAY_SECRET_KEY", "\"$swiftPaySecretKey\"")
        buildConfigField("String", "SWIFTPAY_API_KEY", "\"$swiftPayAccessKey\"")
        buildConfigField("String", "SWIFTPAY_BASE_URL", "\"$swiftPayBaseUrl\"")
        buildConfigField("String", "SWIFTPAY_MERCHANT_ID", "\"$swiftPayMerchantId\"")
        buildConfigField("String", "SWIFTPAY_TERMINAL_ID", "\"$swiftPayTerminalId\"")
        buildConfigField("String", "RESEND_API_KEY", "\"$resendKey\"")
        buildConfigField("String", "OTP_SENDER_EMAIL", "\"$otpSender\"")
        buildConfigField("String", "SWIFTPAY_SUCCESS_REDIRECT_URL", "\"$swiftPaySuccess\"")
        buildConfigField("String", "SWIFTPAY_FAILURE_REDIRECT_URL", "\"$swiftPayFailure\"")
        buildConfigField("String", "SWIFTPAY_CANCEL_REDIRECT_URL", "\"$swiftPayCancel\"")
        buildConfigField("String", "APP_SERVER_URL", "\"${optionalProperty("APP_SERVER_URL")}\"")
        buildConfigField("String", "APP_SERVER_KEY", "\"${appServerKey}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = localProps.getProperty("RELEASE_STORE_FILE")?.let { file(it) }
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Enforce presence of critical server config for release builds
            if (APP_SERVER_URL_PROP.isBlank()) {
                throw GradleException("APP_SERVER_URL must be set in local.properties, environment or gradle properties for release builds")
            }
            if (APP_SERVER_KEY_PROP.isBlank()) {
                throw GradleException("APP_SERVER_KEY must be set in local.properties, environment or gradle properties for release builds")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.compose.adaptive.navigation.suite)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
