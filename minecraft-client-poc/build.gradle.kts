plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val lwjglVersion = "3.3.3"

// LWJGL ships one natives jar per platform; pick the one matching the build host.
val lwjglNatives = System.getProperty("os.name").lowercase().let { os ->
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.contains("win") -> "natives-windows"
        os.contains("mac") || os.contains("darwin") ->
            if (arch.startsWith("aarch64")) "natives-macos-arm64" else "natives-macos"
        arch.startsWith("aarch64") -> "natives-linux-arm64"
        else -> "natives-linux"
    }
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-nanovg")
    implementation("org.lwjgl:lwjgl-stb")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-nanovg::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
}

application {
    mainClass = "dev.poc.client.PocDemo"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-serial,-this-escape")
}

/** Headless walkthrough of the module, keybind and version-switch subsystems. */
tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "Runs the headless subsystem demo (no GPU required)."
    mainClass = "dev.poc.client.PocDemo"
    classpath = sourceSets["main"].runtimeClasspath
}

/** Opens the animated main menu. Needs a display; on macOS it also needs -XstartOnFirstThread. */
tasks.register<JavaExec>("runUi") {
    group = "application"
    description = "Opens the NanoVG main menu."
    mainClass = "dev.poc.client.ui.UiDemo"
    classpath = sourceSets["main"].runtimeClasspath
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}
