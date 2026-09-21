plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-support"))
    api(project(":ontrack-ui-support"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(project(":ontrack-extension-queue"))
    implementation(project(":ontrack-extension-notifications"))
    implementation(project(":ontrack-ui-graphql"))
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    implementation("io.micrometer:micrometer-core")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-config"))
    implementation(project(":ontrack-repository-support"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))
    testImplementation(testFixtures(project(":ontrack-extension-notifications")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation("com.networknt:json-schema-validator")

    testFixturesImplementation("org.jetbrains.kotlin:kotlin-test")
    testFixturesImplementation(testFixtures(project(":ontrack-it-utils")))
    testFixturesImplementation(testFixtures(project(":ontrack-model")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
