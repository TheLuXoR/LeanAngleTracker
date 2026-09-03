plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.android.gms.oss-licenses-plugin")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

val legalProviderName = providers.gradleProperty("LEGAL_PROVIDER_NAME")
    .orElse("NOT CONFIGURED — RELEASE BLOCKED")
val legalProviderAddress = providers.gradleProperty("LEGAL_PROVIDER_ADDRESS")
    .orElse("NOT CONFIGURED — RELEASE BLOCKED")
val legalProviderEmail = providers.gradleProperty("LEGAL_PROVIDER_EMAIL")
    .orElse("NOT CONFIGURED — RELEASE BLOCKED")
val privacyPolicyUrl = providers.gradleProperty("PRIVACY_POLICY_URL")
    .orElse("")
val configuredMapTileUrl = providers.gradleProperty("MAP_TILE_URL")
val mapTileUrl = configuredMapTileUrl.orElse("https://tile.openstreetmap.org/")

android {
    namespace = "de.hasselmeyer.leanangle"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.hasselmeyer.leanangle"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        buildConfigField("String", "AUTOMATION_PACK_PRODUCT_ID", "\"automation_pack_unlock\"")
        buildConfigField("String", "PREMIUM_SUBSCRIPTION_PRODUCT_ID", "\"premium_subscription\"")
        buildConfigField("String", "LEGAL_PROVIDER_NAME", legalProviderName.get().asBuildConfigString())
        buildConfigField("String", "LEGAL_PROVIDER_ADDRESS", legalProviderAddress.get().asBuildConfigString())
        buildConfigField("String", "LEGAL_PROVIDER_EMAIL", legalProviderEmail.get().asBuildConfigString())
        buildConfigField("String", "PRIVACY_POLICY_URL", privacyPolicyUrl.get().asBuildConfigString())
        buildConfigField("String", "MAP_TILE_URL", mapTileUrl.get().asBuildConfigString())
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    // Optional: Using Toolchains is the recommended modern way
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-1476540026076343/8052650023\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-1476540026076343/2598110960\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-1476540026076343~1854739460"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.compose.animation:animation:1.12.0")
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    androidTestImplementation(bom)


    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.1")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val verifyReleaseLegalConfiguration by tasks.registering {
    group = "verification"
    description = "Fails release builds until real provider and privacy-policy details are configured."

    doLast {
        val requiredProperties = mapOf(
            "LEGAL_PROVIDER_NAME" to legalProviderName.get(),
            "LEGAL_PROVIDER_ADDRESS" to legalProviderAddress.get(),
            "LEGAL_PROVIDER_EMAIL" to legalProviderEmail.get(),
            "PRIVACY_POLICY_URL" to privacyPolicyUrl.get(),
            "MAP_TILE_URL" to configuredMapTileUrl.orNull.orEmpty()
        )
        val missing = requiredProperties
            .filterValues { it.isBlank() || it.startsWith("NOT CONFIGURED") }
            .keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release blocked. Configure these Gradle properties with real legal details: " +
                    missing.joinToString()
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseLegalConfiguration)
}
