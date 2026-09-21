plugins {
    `java-library`
}

dependencies {
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation(project(":ontrack-model"))
    implementation("org.slf4j:slf4j-api")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
}
