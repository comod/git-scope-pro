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
        bundledModule("intellij.platform.rpc")
    }
}
