//import org.jetbrains.changelog.Changelog
//import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = project.findProperty(key).toString()

//fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)
//https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html#components-of-a-wizard-generated-gradle-intellij-platform-plugin
plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.12.0"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.0.0"
    // Gradle Qodana Plugin
//    id("org.jetbrains.qodana") version "0.1.13"
//    // Gradle Kover Plugin
//    id("org.jetbrains.kotlinx.kover") version "0.6.1"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
//    jvmToolchain(11)
    jvmToolchain(17)
}
//// Configure Gradle IntelliJ Plugin
//// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName.set(properties("pluginName"))
//    version.set(properties("platformVersion"))
    version.set("LATEST-EAP-SNAPSHOT")
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
//    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
    plugins.set(listOf("Git4Idea"))
}
// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.set(emptyList())
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.reactivex.rxjava3:rxjava:3.1.6")
}
tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    // Set the JVM compatibility versions
//    withType<JavaCompile> {
//
////        sourceCompatibility = "11"
////        targetCompatibility = "11"
//        sourceCompatibility = "17"
//        targetCompatibility = "17"
//    }

//    patchPluginXml {
//        version.set(properties("pluginVersion"))
//        sinceBuild.set(properties("pluginSinceBuild"))
//        untilBuild.set(properties("pluginUntilBuild"))
//
//        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
//        pluginDescription.set(
//                file("README.md").readText().lines().run {
//                    val start = "<!-- Plugin description -->"
//                    val end = "<!-- Plugin description end -->"
//
//                    if (!containsAll(listOf(start, end))) {
//                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
//                    }
//                    subList(indexOf(start) + 1, indexOf(end))
//                }.joinToString("\n").let { markdownToHTML(it) }
//        )
//
//        changeNotes.set(
//                file("README.md").readText().lines().run {
//                    val start = "<!-- Plugin changelog -->"
//                    val end = "<!-- Plugin changelog end -->"
//
//                    if (!containsAll(listOf(start, end))) {
//                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
//                    }
//                    subList(indexOf(start) + 1, indexOf(end))
//                }.joinToString("\n").let { markdownToHTML(it) }
//        )
    // Get the latest available change notes from the changelog file
//        changeNotes.set(provider {
//            with(changelog) {
//                renderItem(
//                        getOrNull(properties("pluginVersion"))
//                                ?: runCatching { getLatest() }.getOrElse { getUnreleased() },
//                        Changelog.OutputType.HTML,
//                )
//            }
//        })

//    }
    patchPluginXml {
        // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#intellij-platform-based-products-of-recent-ide-versions
        // keep in sync with resources/META-INF/plugin.xml
        // 305
//        sinceBuild.set("211")
//        untilBuild.set("222")

        // 306
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))
    }

//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }
//
//    publishPlugin {
//        token.set(System.getenv("PUBLISH_TOKEN"))
//    }
}
