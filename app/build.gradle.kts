plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── Versão obrigatória em release ────────────────────────────────────────────
val versaoArquivo: String? by lazy {
    val f = rootProject.file("release/versao.txt")
    if (f.exists()) f.readText().trim().takeIf { it.isNotBlank() } else null
}
val versaoFinal: String = (findProperty("versao") as String?)
    ?: versaoArquivo
    ?: ""
val codigoFinal: Int = (findProperty("codigo") as String?)?.toIntOrNull()
    ?: versaoFinal.replace(Regex("[^0-9]"), "").take(6).toIntOrNull()
    ?: 0

android {
    namespace = "br.to.seduc.sgediariowidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.to.seduc.sgediariowidget"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = codigoFinal
        versionName = versaoFinal
    }

    sourceSets["main"].manifest.srcFile("../AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/main/res")

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val arquivo = System.getenv("KEYSTORE_FILE")
            if (arquivo != null) {
                storeFile = file(arquivo)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?.takeIf { it.isNotBlank() }
                    ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".teste"
            resValue("string", "app_name", "SGE Diário (teste)")
        }
        release {
            require(versaoFinal.isNotBlank()) {
                "Versão não definida. Passe -Pversao=X.Y ou crie release/versao.txt."
            }
            require(codigoFinal > 0) {
                "Código de versão inválido ($codigoFinal). Passe -Pcodigo=N (N > 0)."
            }
            resValue("string", "app_name", "SGE Diário")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; resValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Keystore obrigatório em release: verificado no momento da tarefa, não da configuração.
// O AGP 9 avalia todos os buildTypes na fase de configuração; o require diferido aqui
// falha alto antes de gravar o APK, sem quebrar assembleDebug em máquina sem keystore.
afterEvaluate {
    tasks.matching { it.name.startsWith("package") && it.name.contains("Release") }.configureEach {
        doFirst {
            require(System.getenv("KEYSTORE_FILE") != null) {
                "KEYSTORE_FILE não definido. " +
                "Build de release sem keystore geraria APK não assinado. " +
                "Use assembleDebug ou defina KEYSTORE_BASE64 no CI."
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jsoup:jsoup:1.17.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.mockk:mockk:1.13.12")
}
