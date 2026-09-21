plugins {
    `java-library`
}

description = "JSON utilities."

dependencies {
    api("tools.jackson.core:jackson-databind")

    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.apache.commons:commons-lang3")
}
