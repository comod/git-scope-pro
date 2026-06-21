import org.jetbrains.changelog.Changelog.OutputType
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

fun properties(key: String) = project.findProperty(key).toString()

val platformVersion = properties("platformVersion")
val platformType = properties("platformType")

plugins {
    application
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.intellij.platform.module") version "2.16.0" apply false
    id("org.jetbrains.changelog") version "2.5.0"
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("rpc") version "2.3.20-0.1" apply false
}

group = properties("pluginGroup")
version = properties("pluginVersion")

subprojects {

    afterEvaluate {
        extensions.findByType<KotlinJvmProjectExtension>()?.jvmToolchain(21)
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

allprojects {
    repositories {
        mavenCentral()
    }
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}


fun getMajorVersion(version: String): String {
    val parts = version.split(".")
    return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
}

changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
    headerParserRegex.set("""(\d{4}\.\d+(?:\.\d+)?)""".toRegex())
    keepUnreleasedSection.set(false)
}

dependencies {
    intellijPlatform {
        val type: String = providers.gradleProperty("platformType").get()
        val version: String = providers.gradleProperty("platformVersion").get()

        create(type, version) {
            useInstaller = !version.endsWith("EAP-SNAPSHOT")
        }
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        pluginModule(implementation(project(":shared")))
        pluginModule(implementation(project(":backend")))
        pluginModule(implementation(project(":frontend")))
    }
}

intellijPlatform {

    splitMode = true
    pluginInstallationTarget = PluginInstallationTarget.BOTH

    signing {
        certificateChainFile = providers.environmentVariable("CERTIFICATE_CHAIN_FILE")
            .map { File(it) }
        privateKeyFile = providers.environmentVariable("PRIVATE_KEY_FILE")
            .map { File(it) }
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    pluginConfiguration {
        changeNotes.set(provider {
            val majorVersion = getMajorVersion(project.version.toString())

            val matchingEntries = changelog.getAll().values
                .filter { it.version.startsWith(majorVersion) }
            if (matchingEntries.isNotEmpty()) {
                matchingEntries.joinToString("\n\n") {
                    changelog.renderItem(it, OutputType.HTML)
                }
            } else {
                changelog.renderItem(
                    changelog.getOrNull(properties("pluginVersion"))
                        ?: changelog.getLatest(),
                    OutputType.HTML
                )
            }
        })
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

}

// Split Mode run task — launches frontend + backend processes locally for remote dev testing.
// Usage: ./gradlew runIdeSplitMode
val runIdeSplitMode by intellijPlatformTesting.runIde.registering {
    splitMode = true
    pluginInstallationTarget = PluginInstallationTarget.BOTH
}

// Monolith run task — single-process mode for everyday development.
// Usage: ./gradlew runIdeMonolith
val runIdeMonolith by intellijPlatformTesting.runIde.registering {
    splitMode = false
}

gradle.taskGraph.whenReady {
    val isRelease = hasTask(":signPlugin") || hasTask(":publishPlugin") || hasTask(":verifyPlugin")
    tasks.named("buildSearchableOptions") { enabled = isRelease }
    tasks.named("prepareJarSearchableOptions") { enabled = isRelease }
    tasks.named("jarSearchableOptions") { enabled = isRelease }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
        distributionType = Wrapper.DistributionType.BIN
    }

    register<DefaultTask>("verifyWrapperVersion") {
        description = "Verifies that the Gradle Wrapper version matches the gradleVersion property."
        val expectedVersion = providers.gradleProperty("gradleVersion").orElse("")
        inputs.property("expectedGradleVersion", expectedVersion)

        doLast {
            val expected = inputs.properties["expectedGradleVersion"] as String
            if (expected.isBlank()) return@doLast

            val actual = GradleVersion.current().version
            if (expected != actual) {
                throw GradleException(
                    "Gradle Wrapper is $actual but expected is gradleVersion=$expected. " +
                            "Run: ./gradlew wrapper --gradle-version $expected"
                )
            }
        }
    }
}

listOf("build", "buildPlugin").forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        dependsOn(tasks.named("verifyWrapperVersion"))
    }
}

