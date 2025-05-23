plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.synthcontroller"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.synthcontroller"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}


dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.github.ahmmedrejowan:RotaryKnob:0.1")
    implementation("com.github.convergencelab:PianoView:v0.1")
    implementation("com.google.code.gson:gson:2.10.1")
}
