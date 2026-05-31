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
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        bundledModule("intellij.platform.backend")
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
    }

    implementation(project(":shared"))
    compileOnly("com.google.code.gson:gson:2.13.2")
}
