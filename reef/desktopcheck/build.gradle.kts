import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Local-only check: compiles the app's UI package (everything except MainActivity) against
// Compose Desktop, so the Compose code can be type-checked without Google's Maven repository.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets["main"].kotlin.srcDir("../app/src/main/kotlin/com/reef/app/ui")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":engine"))
    val compose = "1.7.3"
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$compose")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$compose")
    implementation("org.jetbrains.compose.material3:material3-desktop:$compose")
    implementation("org.jetbrains.compose.ui:ui-desktop:$compose")
    runtimeOnly("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$compose")
}

// Renders the app's screens to PNGs: ./gradlew :desktopcheck:screenshots -PengineOnly -PdesktopCheck -PscreensDir=...
tasks.register<JavaExec>("screenshots") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.reef.app.ui.ScreenshotsKt")
    jvmArgs("-Djava.awt.headless=true")
    args(providers.gradleProperty("screensDir").getOrElse(layout.buildDirectory.dir("screens").get().asFile.path))
}
