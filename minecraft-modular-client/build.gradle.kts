plugins {
    java
}

allprojects {
    group = "dev.poc"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")        // tiny-remapper, mapping-io
        maven("https://repo.spongepowered.org/repository/maven-public/") // mixin
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }
}

// Le natif LWJGL dépend de la plateforme hôte : résolu une seule fois, dans le shell.
val lwjglNatives: String by extra(
    when {
        org.gradle.internal.os.OperatingSystem.current().isWindows -> "natives-windows"
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "natives-macos"
        else -> "natives-linux"
    }
)
