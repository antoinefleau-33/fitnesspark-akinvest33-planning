plugins { java }

val lwjglNatives = when {
    org.gradle.internal.os.OperatingSystem.current().isWindows -> "natives-windows"
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":poc-api"))
    implementation(project(":poc-core"))

    implementation(platform("org.lwjgl:lwjgl-bom:3.3.4"))
    listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb", "lwjgl-nanovg").forEach {
        implementation("org.lwjgl:$it")
        // Les natifs vivent UNIQUEMENT ici : le shell les charge une fois pour toute la JVM.
        // Aucun classloader de version ne doit en embarquer une seconde copie.
        runtimeOnly("org.lwjgl:$it::$lwjglNatives")
    }
}

tasks.withType<JavaExec>().configureEach {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        // GLFW exige le thread principal sur macOS.
        jvmArgs("-XstartOnFirstThread")
    }
}
