plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.luctube"
version = "1.0.0"

application {
    mainClass.set("com.luctube.ApplicationKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks {
    shadowJar {
        archiveBaseName.set("newpipe-api")
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
    }
}
