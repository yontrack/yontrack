plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-ui-graphql"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("io.micrometer:micrometer-core")

    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation("com.networknt:json-schema-validator")

    testFixturesImplementation(testFixtures(project(":ontrack-it-utils")))
    testFixturesImplementation("com.networknt:json-schema-validator")

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))

}
