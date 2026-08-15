// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // apply false: legt KGP 2.3.20 auf den Classpath. AGPs Built-in Kotlin
    // nutzt diese Version statt seines gebuendelten Minimums; angewendet wird
    // das Plugin nirgends (Built-in Kotlin kompiliert alle Module).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
