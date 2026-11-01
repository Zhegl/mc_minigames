plugins {
    java
}

group = "me.petr1furious"
version = "0.2.0"

// Which Minecraft API to compile against: `-Pmc=26.2` for the older dev server,
// anything else (the default) for 26.3. The jar name says which one it is.
val mc = providers.gradleProperty("mc").getOrElse("26.3")

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
    compileOnly(if (mc == "26.2") libs.paper.api.legacy else libs.paper.api)
    compileOnly(libs.asp.api)
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.jar {
    archiveClassifier.set("mc$mc")
}

tasks.processResources {
    val pluginVersion = version.toString()
    // Declared, or a version bump leaves the old plugin.yml in place.
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

val checks by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

val checkPillarsPresets by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.PillarsPresetsCheck")
}

val checkSniperDuel by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.SniperDuelCheck")
}

val checkPartyPvp by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PvpCheck")
}

val checkPartySumo by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.SumoCheck")
}

val checkPartyArmsRace by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.ArmsRaceCheck")
}

tasks.check {
    dependsOn(checkPillarsPresets, checkSniperDuel, checkPartyPvp, checkPartySumo, checkPartyArmsRace)
}
