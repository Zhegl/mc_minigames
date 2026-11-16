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

sourceSets.main {
    java.srcDir("i18n/java")
}

tasks.processResources {
    val pluginVersion = version.toString()
    val apiVersion = mc
    from("i18n/locales") {
        into("i18n")
    }
    // Declared, or a version bump leaves stale plugin metadata in place.
    inputs.property("version", pluginVersion)
    inputs.property("apiVersion", apiVersion)
    filesMatching("paper-plugin.yml") {
        expand("version" to pluginVersion, "apiVersion" to apiVersion)
    }
}

val checks by sourceSets.creating {
    java.srcDir("i18n/checks")
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

val checkPillarsMath by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.MathChallengeCheck")
}

val checkPillarsShuffle by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.ShuffleItemsCheck")
}

val checkPillarsAdhd by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.AdhdModifierCheck")
}

val checkPillarsFlood by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.FloodModifierCheck")
}

val checkPillarsSky by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.PillarsSkyCheck")
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

val checkPartySurvival by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartySurvivalCheck")
}

val checkPartyRuntime by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyRuntimeCheck")
}

val checkPvpClasses by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PvpClassesCheck")
}

val checkPartyGroupCombat by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.GroupCombatCheck")
}

val checkPartyMiners by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.MinersCheck")
}

val checkPillarsPerks by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.pillars.PerksCheck")
}

tasks.check {
    dependsOn(checkPillarsPresets, checkSniperDuel, checkPillarsMath, checkPillarsShuffle, checkPillarsAdhd, checkPillarsFlood, checkPillarsSky,
            checkPartyPvp, checkPartySumo, checkPartyArmsRace, checkPartySurvival, checkPartyRuntime, checkPvpClasses, checkPartyGroupCombat, checkPartyMiners, checkPillarsPerks)
}

val checkPartyScoring by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyScoringCheck")
}
tasks.check { dependsOn(checkPartyScoring) }

val checkPartyLifecycle by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyLifecycleCheck")
}
tasks.check { dependsOn(checkPartyLifecycle) }

for ((taskName, checkClass) in mapOf(
    "checkColorFloor" to "ColorFloorCheck", "checkAnvils" to "AnvilsCheck",
    "checkBuildMemory" to "BuildMemoryCheck", "checkToolRace" to "ToolRaceCheck",
    "checkOneShot" to "OneShotCheck", "checkParkourRace" to "ParkourRaceCheck")) {
    val gameCheck = tasks.register<JavaExec>(taskName) {
        dependsOn(checks.classesTaskName)
        classpath = checks.runtimeClasspath
        mainClass.set("me.petr1furious.potatominigames.games.party.$checkClass")
    }
    tasks.check { dependsOn(gameCheck) }
}

val checkPartyFfa by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyFfaCheck")
}
tasks.check { dependsOn(checkPartyFfa) }

val checkPartyGames by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyGamesCheck")
}

val checkGarticPhone by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.GarticPhoneCheck")
}

tasks.check { dependsOn(checkGarticPhone) }
tasks.check { dependsOn(checkPartyGames) }
val checkPartyArenas by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyArenaCheck")
}
tasks.check { dependsOn(checkPartyArenas) }
val checkPartyRouting by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyRoutingCheck")
}
tasks.check { dependsOn(checkPartyRouting) }
val checkPartyInteractions by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyInteractionCheck")
}
tasks.check { dependsOn(checkPartyInteractions) }
val checkPartyPolish by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.PartyPolishCheck")
}
tasks.check { dependsOn(checkPartyPolish) }

val checkHoleWall by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.HoleWallCheck")
}
tasks.check { dependsOn(checkHoleWall) }

val checkI18n by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.i18n.I18nCheck")
}
tasks.check { dependsOn(checkI18n) }

val checkFinals by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.finals.FinalsStateCheck")
}

val checkFinalsArena by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.finals.FinalsArenaCheck")
}
tasks.check { dependsOn(checkFinals, checkFinalsArena) }

val checkFinalsSession by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.finals.FinalsSessionCheck")
}
tasks.check { dependsOn(checkFinalsSession) }


for ((taskName, checkClass) in mapOf(
    "checkFinalsTutorial" to "FinalsTutorialCheck",
    "checkFinalsWeapons" to "FinalsWeaponsCheck",
    "checkFinalsRegeneration" to "FinalsRegenerationCheck",
    "checkFinalsHitHealth" to "FinalsHitHealthCheck",
    "checkFinalsGadgets" to "FinalsGadgetsCheck",
    "checkFinalsEquipment" to "FinalsEquipmentCheck",
    "checkFinalsMaps" to "FinalsMapsCheck",
    "checkFinalsMapJumpPads" to "FinalsMapJumpPadsCheck",
    "checkFinalsMapPreview" to "FinalsMapPreviewCheck",
    "checkFinalsHighlights" to "FinalsHighlightsCheck",
    "checkFinalsKills" to "FinalsKillsCheck",
    "checkFinalsTabList" to "FinalsTabListCheck",
    "checkFinalsCashboxFlight" to "FinalsCashboxFlightCheck",
    "checkFinalsLoadouts" to "FinalsLoadoutsCheck",
    "checkFinalsTeamWipes" to "FinalsTeamWipesCheck",
    "checkFinalsRevives" to "FinalsRevivesCheck",
    "checkFinalsSlimeSnapshot" to "FinalsSlimeSnapshotCheck",
    "checkFinalsMapDownload" to "FinalsMapDownloadCheck",
    "checkFinalsCashoutMusic" to "FinalsCashoutMusicCheck",
    "checkFinalsCashoutPhysics" to "FinalsCashoutPhysicsCheck")) {
    val finalsCheck = tasks.register<JavaExec>(taskName) {
        dependsOn(checks.classesTaskName)
        classpath = checks.runtimeClasspath
        mainClass.set("me.petr1furious.potatominigames.games.finals.$checkClass")
    }
    tasks.check { dependsOn(finalsCheck) }
}

val checkFinalsMapImport by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.finals.FinalsMapImportCheck")
    maxHeapSize = "64m"
}
tasks.check { dependsOn(checkFinalsMapImport) }

val checkMagicBattle by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.party.MagicBattleCheck")
}
tasks.check { dependsOn(checkMagicBattle) }

val checkCounterStrike by tasks.registering(JavaExec::class) {
    dependsOn(checks.classesTaskName)
    classpath = checks.runtimeClasspath
    mainClass.set("me.petr1furious.potatominigames.games.cs.CsCheck")
}
tasks.check { dependsOn(checkCounterStrike) }
