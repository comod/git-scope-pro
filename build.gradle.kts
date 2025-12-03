
fun properties(key: String) = project.findProperty(key).toString()

val platformVersion = properties("platformVersion")
val platformType = properties("platformType")

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.changelog") version "2.5.0"
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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

fun getMajorVersion(version: String): String {
    val parts = version.split(".")
    return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
}

changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
    // Configure to accept your version format (YYYY.N or YYYY.N.N)
    headerParserRegex.set("""(\d{4}\.\d+(?:\.\d+)?)""".toRegex())
    keepUnreleasedSection.set(true)
}

dependencies {
    intellijPlatform {
        val type: String = providers.gradleProperty("platformType").get()
        val version: String = providers.gradleProperty("platformVersion").get()


        create(type, version) {
            useInstaller = !version.endsWith("EAP-SNAPSHOT")
        }
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
    }
}

intellijPlatform {
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

            // Get all changelog entries that start with the major version
            val matchingEntries = changelog.getAll().values
                .filter { it.version.startsWith(majorVersion) }
            if (matchingEntries.isNotEmpty()) {
                matchingEntries.joinToString("\n\n") {
                    changelog.renderItem(it, org.jetbrains.changelog.Changelog.OutputType.HTML)
                }
            } else {
                // Fallback to current version only
                changelog.renderItem(
                    changelog.getOrNull(properties("pluginVersion"))
                        ?: changelog.getLatest(),
                    org.jetbrains.changelog.Changelog.OutputType.HTML
                )
            }
        })
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    prepareJarSearchableOptions {
        enabled = false
    }
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
        distributionType = Wrapper.DistributionType.BIN
    }

    register<DefaultTask>("verifyWrapperVersion") {
        // Wire expected version as a declared input so the action doesn't capture Project
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
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}


// Verify that we have the expected version of the Gradle wrapper
listOf("build", "buildPlugin").forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        dependsOn(tasks.named("verifyWrapperVersion"))
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("io.reactivex.rxjava3:rxjava:3.1.10")
}