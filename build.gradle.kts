plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        local("/Applications/IntelliJ IDEA.app")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(17)
}
