plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-json"))
    api(project(":ontrack-common"))
    api("org.springframework:spring-tx")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(project(":ontrack-job"))
    implementation("org.apache.commons:commons-text")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(project(":ontrack-test-utils"))

    testFixturesImplementation("org.springframework:spring-context")
}
