import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    `java-library`
    java
    distribution
    kotlin("jvm") version "1.6.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

apply(plugin = "idea")
apply(plugin = "java")
apply(plugin = "kotlin")

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.slf4j:slf4j-api:1.7.33")
    implementation("org.slf4j:slf4j-log4j12:1.7.33")

    implementation("io.netty:netty-all:4.1.77.Final")
    implementation("it.unimi.dsi:fastutil:8.2.2")
    implementation("org.apache.commons:commons-math3:3.6.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        // Target version of the generated JVM bytecode (1.6 or 1.8), default is 1.6
        jvmTarget = "11"

    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}