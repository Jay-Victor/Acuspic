import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.acuspic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.acuspic.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
        
        buildToolsVersion = "36.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFilePath = localProperties.getProperty("KEYSTORE_FILE")
            val keystorePasswordValue = localProperties.getProperty("KEYSTORE_PASSWORD")
            val keyAliasValue = localProperties.getProperty("KEYSTORE_ALIAS")
            val keyPasswordValue = localProperties.getProperty("KEY_PASSWORD")

            if (keystoreFilePath == null || keystorePasswordValue == null || keyAliasValue == null || keyPasswordValue == null) {
                throw GradleException("""
                    签名密钥配置不完整！请在 local.properties 中配置以下项：
                    - KEYSTORE_FILE=acuspic.p12
                    - KEYSTORE_PASSWORD=您的密钥库密码
                    - KEYSTORE_ALIAS=acuspic
                    - KEY_PASSWORD=您的密钥密码
                """.trimIndent())
            }

            storeFile = file("${rootDir}/../$keystoreFilePath")
            storePassword = keystorePasswordValue
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    
    lint {
        checkReleaseBuilds = true
        abortOnError = false
        warningsAsErrors = false
        ignoreWarnings = false
        disable += setOf(
            "TypographyFractions",
            "TypographyQuotes",
            "Typos"
        )
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    applicationVariants.all {
        outputs.forEach { output ->
            output as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val outputFileName = "Acuspic-v${versionName}.Apk"
            output.outputFileName = outputFileName
        }
    }
}

configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Markwon - Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")
    
    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Activity result contracts
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Glide - Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Timber - Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
