plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-git"))

    implementation(project(":ontrack-ui-graphql"))
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-codec:commons-codec")
    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-extension-general"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-indicators"))
    implementation(project(":ontrack-extension-auto-versioning"))
    implementation(project(":ontrack-extension-config"))
    implementation(project(":ontrack-extension-notifications"))
    implementation("io.jsonwebtoken:jjwt-api")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("io.micrometer:micrometer-core")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation(project(":ontrack-rabbitmq"))

    runtimeOnly("io.jsonwebtoken:jjwt-impl")

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation(project(":ontrack-repository"))
    testImplementation(project(":ontrack-extension-stale"))
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-extension-auto-versioning")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-general")))
    testImplementation(testFixtures(project(":ontrack-extension-issues")))
    testImplementation(testFixtures(project(":ontrack-extension-git")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))

    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")
    testFixturesImplementation(project(":ontrack-test-utils"))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}