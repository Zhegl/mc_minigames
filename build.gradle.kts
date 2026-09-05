plugins {
    java
}

group = "me.petr1furious"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/") {
        name = "infernalsuite"
        content {
            includeGroup("com.infernalsuite.asp")
        }
    }
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.asp.api)
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    val pluginVersion = version.toString()
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
