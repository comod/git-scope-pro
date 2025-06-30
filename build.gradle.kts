import java.io.*
import java.net.*
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants
import org.w3c.dom.Document
import org.gradle.api.provider.Provider

fun properties(key: String) = project.findProperty(key).toString()

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

// pluginIdeaVersion comes from properties and it set to LATEST-STABLE, LATEST-EAP-SNAPSHOT, or a specific version number
val ideaVersion = findIdeaVersion(providers.gradleProperty("platformVersion"))
dependencies {
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        create(type, ideaVersion)
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        pluginVerifier()
    }
}

intellijPlatform {
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