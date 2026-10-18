plugins {
    id("org.jetbrains.intellij.platform.module")
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("rpc")
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Snapshot builds have no installer, so they come from the Maven snapshot repository.
        val platformVersion = providers.gradleProperty("platformVersion").get()
        intellijIdea(platformVersion) {
            useInstaller = !platformVersion.endsWith("EAP-SNAPSHOT")
        }
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        bundledModule("intellij.platform.backend")
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        // 2026.2 split VCS APIs into finer content modules that are no longer pulled in
        // transitively via the Git4Idea plugin, so declare them explicitly.
        bundledModule("intellij.platform.vcs.core")
        bundledModule("intellij.platform.vcs.shared")
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.impl.shared")
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        bundledModule("intellij.platform.vcs.dvcs.impl.shared")
        bundledModule("intellij.platform.vcs.log.impl")
        // 2026.3 moved the platform VCS APIs out of the core into the bundled Version Control plugin.
        if (isVcsBundledPlugin(platformVersion)) {
            bundledPlugin("com.intellij.platform.vcs")
        }
    }

    implementation(project(":shared"))
    compileOnly("com.google.code.gson:gson:2.14.0")
    testImplementation("junit:junit:4.13.2")
}

/** True for platform versions where the VCS APIs ship as the com.intellij.platform.vcs plugin (2026.3+). */
fun isVcsBundledPlugin(version: String): Boolean {
    if (version.endsWith("EAP-SNAPSHOT")) return true
    val parts = version.split('.').map { it.toIntOrNull() ?: 0 }
    val major = parts[0]
    return if (major > 1000) major > 2026 || (major == 2026 && parts.getOrElse(1) { 0 } >= 3) else major >= 263
}
