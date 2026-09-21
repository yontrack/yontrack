plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-ui-support"))
    api(project(":ontrack-tx"))
    api("org.springframework.graphql:spring-graphql")

    implementation(project(":ontrack-job"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.apache.commons:commons-lang3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":ontrack-test-utils"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(project(":ontrack-extension-general"))
    testImplementation(testFixtures(project(":ontrack-extension-general")))

    testFixturesImplementation(testFixtures(project(":ontrack-it-utils")))
    testFixturesImplementation("org.jetbrains.kotlin:kotlin-test")

    testRuntimeOnly(project(":ontrack-repository-impl"))
    testRuntimeOnly(project(":ontrack-service"))
}
