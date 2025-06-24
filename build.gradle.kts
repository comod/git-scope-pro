import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = project.findProperty(key).toString()
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.jetbrains.changelog") version "2.1.2"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        val version = providers.gradleProperty("platformVersion")
        create(type, version)
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
    }
}

intellijPlatform {
    val ideaVersion = properties("platformVersion")
    val ideaPlugins = providers.gradleProperty("platformPlugins")
    val ideaPluginsList = ideaPlugins.map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.reactivex.rxjava3:rxjava:3.1.6")
}
