plugins {
    alias(libs.plugins.android.library)
    checkstyle
}

android {
    namespace = "app.outfitshare.core.designsystem"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Тема и стили наследуют Material 3, поэтому Material и AppCompat отдаются потребителям как api.
    api(libs.material)
    api(libs.androidx.appcompat)
    api(libs.androidx.core)
    api(libs.androidx.core.splashscreen)
    implementation(libs.androidx.dynamicanimation)

    testImplementation(libs.junit)
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.register<Checkstyle>("checkstyle") {
    description = "Checkstyle для Java-исходников дизайн-системы."
    group = "verification"
    source("src")
    include("**/*.java")
    exclude("**/tokens/Ds*Tokens.java")
    classpath = files()
}

tasks.named("check") {
    dependsOn("checkstyle")
}
