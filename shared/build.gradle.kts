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
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledModule("intellij.platform.rpc")
    }
}
