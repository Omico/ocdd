import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant {
            sourceSetTree = KotlinSourceSetTree.test
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "OcddIo"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings {
                enableLanguageFeature("ExpectActualClasses")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.okio.android)
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.test.runner)
        }
        iosMain.dependencies {
            implementation(libs.okio.ios)
        }
    }
}

android {
    namespace = "me.omico.ocdd.io"
    //noinspection GradleDependency
    compileSdk = 34
    ndkVersion = "21.1.6352462"

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}

dependencyLocking {
    lockAllConfigurations()
}
