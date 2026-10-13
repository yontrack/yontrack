plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-repository"))
    api("org.springframework:spring-jdbc")

    implementation(project(":ontrack-database"))
    implementation(project(":ontrack-repository-support"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-actuator")
    implementation("org.slf4j:slf4j-api")
    implementation("org.flywaydb:flyway-core")
    implementation("org.apache.commons:commons-lang3")

    testImplementation(testFixtures(project(":ontrack-it-utils")))

    testRuntimeOnly(project(":ontrack-service"))
}
