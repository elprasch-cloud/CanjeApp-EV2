// Archivo de build de nivel superior.
// AGP 9.x trae Kotlin integrado ("built-in Kotlin"), por eso ya no se declara
// el plugin org.jetbrains.kotlin.android (era el que causaba el error
// "Unable to load class 'org.gradle.api.internal.HasConvention'" con Gradle 9).
plugins {
    id("com.android.application") version "9.4.1" apply false
}
