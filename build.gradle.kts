plugins {
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
}

val ktfmt by configurations.creating

dependencies { ktfmt("com.facebook:ktfmt:0.64:with-dependencies") }

val kotlinSources =
    fileTree(rootDir) {
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", ".gradle/**", ".kotlin/**", "artifacts/**")
    }

tasks.register<JavaExec>("formatKotlin") {
    classpath = ktfmt
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style")
    args(kotlinSources.files.sortedBy { it.path }.map { it.path })
}

tasks.register<JavaExec>("checkKotlinFormat") {
    classpath = ktfmt
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style", "--dry-run", "--set-exit-if-changed")
    args(kotlinSources.files.sortedBy { it.path }.map { it.path })
}
