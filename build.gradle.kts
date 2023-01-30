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
//    version.set("LATEST-EAP-SNAPSHOT")
//    version.set("2022.1.4")
//    version.set("2022.1.4")
    version.set("2022.2")
//    type.set("IU") // Target IDE Platform
//    type.set("IC") // Target IDE Platform
    type.set("PS") // Target IDE Platform

    plugins.set(listOf("git4idea"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    patchPluginXml {
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
