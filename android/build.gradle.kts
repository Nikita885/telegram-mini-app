plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.spotless)
}

spotless {
    java {
        target("**/src/**/*.java")
        // Файлы токенов генерируются tools/designsystem/generate_tokens.py.
        targetExclude("**/build/**", "**/designsystem/tokens/Ds*Tokens.java")
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
