//https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html#components-of-a-wizard-generated-gradle-intellij-platform-plugin
plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.10.1"
}

group = "GitScope"
version = "3.0.6"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
//    version.set("LATEST-EAP-SNAPSHOT") // git4idea build issue
//    version.set("2022.3.1") // git4idea build issue
//    version.set("2022.3") // git4idea build issue
    version.set("2022.2")
//    version.set("2022.1.4")
//    version.set("2022.2")
//    version.set("2021.1") // 211
//    version.set("2020.3") // 203
    // https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-type
//    type.set("IU")
//    type.set("IC")
    type.set("PS")
//    plugins.set(listOf("com.jetbrains.php:221.5787.21"))

    plugins.set(listOf("git4idea"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    patchPluginXml {
        // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#intellij-platform-based-products-of-recent-ide-versions
        sinceBuild.set("211")
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
