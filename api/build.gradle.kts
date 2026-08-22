plugins { `java-library` }

group = "io.github.miklires"
version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.jar { archiveBaseName.set("mColor-API") }
tasks.named<Jar>("sourcesJar") { archiveBaseName.set("mColor-API") }
