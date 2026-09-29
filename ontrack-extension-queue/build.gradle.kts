plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-support"))
    api(project(":ontrack-rabbitmq"))

    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-recordings"))
    implementation("io.micrometer:micrometer-core")
    implementation("org.slf4j:slf4j-api")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("org.apache.commons:commons-lang3")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-recordings")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation("com.networknt:json-schema-validator")

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation(testFixtures(project(":ontrack-extension-recordings")))
    testFixturesImplementation(testFixtures(project(":ontrack-extension-api")))

    testImplementation(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
