plugins {
    `java-library`
}

dependencies {
    api("org.junit.jupiter:junit-jupiter-api")
    api(project(":ontrack-json"))

    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io")
    implementation("org.jetbrains.kotlin:kotlin-test")
}
