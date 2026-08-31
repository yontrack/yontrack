plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-repository"))
    implementation(project(":ontrack-repository-support"))
    implementation("io.micrometer:micrometer-core")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))

    testImplementation(project(":ontrack-extension-general"))
    testImplementation(project(":ontrack-extension-git"))
    testImplementation(project(":ontrack-extension-scm"))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))

    testFixturesImplementation(project(":ontrack-test-utils"))
    testFixturesImplementation("org.jetbrains.kotlin:kotlin-test")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testFixturesImplementation(project(":ontrack-extension-scm"))
    testFixturesImplementation(testFixtures(project(":ontrack-extension-scm")))
}
