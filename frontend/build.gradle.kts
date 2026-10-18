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
        bundledModule("intellij.platform.frontend")
        // Not pulled in transitively from 2026.3, where VCS moved into its own bundled plugin.
        bundledModule("intellij.platform.vcs.core")
        bundledModule("intellij.platform.vcs.shared")
    }

    implementation(project(":shared"))
}
