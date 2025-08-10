import java.io.*
import java.net.*
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants
import org.w3c.dom.Document
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
    id("org.jetbrains.changelog") version "2.4.0"
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

// Configure changelog plugin
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

// pluginIdeaVersion comes from properties and it set to LATEST-STABLE, LATEST-EAP-SNAPSHOT, or a specific version number
val ideaVersion = findIdeaVersion(providers.gradleProperty("platformVersion"))
dependencies {
    intellijPlatform {
        val type: String = providers.gradleProperty("platformType").get()
        val version: String = ideaVersion
        create(type, version)
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        pluginVerifier()
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
                    changelog.getOrNull(properties("pluginVersion").toString())
                        ?: changelog.getLatest(),
                    org.jetbrains.changelog.Changelog.OutputType.HTML
                )
            }
        })
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }
    }
}

tasks {
    buildSearchableOptions {
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

/**
 * Find the latest stable or EAP IDE version from the JetBrains website, otherwise simply returns the given IDE version string.
 * @param ideaVersion can be LATEST-STABLE, LATEST-EAP-SNAPSHOT or a specific IDE version string (year.maj.min).
 */
fun findIdeaVersion(ideaVersion: Provider<String>): String {
    // Get the actual value from the Provider
    val versionValue = ideaVersion.get()

    /** Find the latest IntelliJ EAP version from the JetBrains website. Result is cached locally for 24h. */
    fun findLatestIdeaVersion(isStable: Boolean): String {

        /** Read a remote file as String. */
        fun readRemoteContent(url: URL): String {
            val t1 = System.currentTimeMillis()
            val content = StringBuilder()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            BufferedReader(InputStreamReader(conn.inputStream)).use { rd ->
                var line: String? = rd.readLine()
                while (line != null) {
                    content.append(line)
                    line = rd.readLine()
                }
            }
            val t2 = System.currentTimeMillis()
            logger.quiet("Download $url, took ${t2 - t1} ms (${content.length} B)")
            return content.toString()
        }

        /** Find the latest IntelliJ version from the given url and xpath expression that picks the desired IDE version and channel.
         * The result is cached for 24hr.*/
        fun getOnlineLatestIdeVersion(definitionsUrl: URL, xpathExpression: String): String {
            val definitionsStr = readRemoteContent(definitionsUrl)
            val builderFactory = DocumentBuilderFactory.newInstance()
            val builder = builderFactory.newDocumentBuilder()
            val xmlDocument: Document = builder.parse(ByteArrayInputStream(definitionsStr.toByteArray()))
            val xPath = XPathFactory.newInstance().newXPath()
            return xPath.compile(xpathExpression).evaluate(xmlDocument, XPathConstants.STRING) as String
        }

        val t1 = System.currentTimeMillis()
        // IMPORTANT if not available, migrate to https://data.services.jetbrains.com/products?code=IC
        @Suppress("DEPRECATION")
        val definitionsUrl = URL("https://www.jetbrains.com/updates/updates.xml")
        val xpathExpression =
            if (isStable) "/products/product[@name='IntelliJ IDEA']/channel[@id='IC-IU-RELEASE-licensing-RELEASE']/build[1]/@version"
            else "/products/product[@name='IntelliJ IDEA']/channel[@id='IC-IU-EAP-licensing-EAP']/build[1]/@fullNumber"
        val cachedLatestVersionFile =
            File(System.getProperty("java.io.tmpdir") + (if (isStable) "/jle-ij-latest-stable-version.txt" else "/jle-ij-latest-eap-version.txt"))
        var latestVersion: String
        try {
            if (cachedLatestVersionFile.exists()) {

                val cacheDurationMs = 24 * 60 * 60_000 // 24hr
                if (cachedLatestVersionFile.exists() && cachedLatestVersionFile.lastModified() < (System.currentTimeMillis() - cacheDurationMs)) {
                    logger.quiet("Cache expired, find latest EAP IDE version from $definitionsUrl then update cached file $cachedLatestVersionFile")
                    latestVersion = getOnlineLatestIdeVersion(definitionsUrl, xpathExpression)
                    cachedLatestVersionFile.delete()
                    Files.writeString(cachedLatestVersionFile.toPath(), latestVersion, Charsets.UTF_8)

                } else {
                    logger.quiet("Find latest EAP IDE version from cached file $cachedLatestVersionFile")
                    latestVersion = Files.readString(cachedLatestVersionFile.toPath())
                }

            } else {
                logger.quiet("Find latest EAP IDE version from $definitionsUrl")
                latestVersion = getOnlineLatestIdeVersion(definitionsUrl, xpathExpression)
                Files.writeString(cachedLatestVersionFile.toPath(), latestVersion, Charsets.UTF_8)
            }

        } catch (e: Exception) {
            if (cachedLatestVersionFile.exists()) {
                logger.warn("Error: ${e.message}. Will find latest EAP IDE version from cached file $cachedLatestVersionFile")
                latestVersion = Files.readString(cachedLatestVersionFile.toPath())
            } else {
                throw RuntimeException(e)
            }
        }
        if (logger.isDebugEnabled) {
            val t2 = System.currentTimeMillis()
            logger.debug("Operation took ${t2 - t1} ms")
        }
        return latestVersion
    }

    if (versionValue == "LATEST-STABLE") {
        val version = findLatestIdeaVersion(true)
        logger.quiet("Found latest stable IDE version: $version")
        return version
    }
    if (versionValue == "LATEST-EAP-SNAPSHOT") {
        val version = findLatestIdeaVersion(false)
        logger.quiet("Found latest EAP IDE version: $version")
        return version
    }
    logger.warn("Will use user-defined IDE version: $versionValue")
    return versionValue
}