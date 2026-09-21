plugins {
    `java-library`
    `java-test-fixtures`
}

configurations.create("testRuntimeExport")

dependencies {
    api(project(":ontrack-extension-scm"))
    api(project(":ontrack-extension-stale"))
    api(project(":ontrack-git"))
    api(project(":ontrack-tx"))
    api(project(":ontrack-json"))

    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-config"))
    implementation("org.springframework:spring-tx")
    implementation("commons-io:commons-io")
    implementation("org.apache.commons:commons-lang3")
    implementation("io.micrometer:micrometer-core")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-extension-issues")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))

    testFixturesImplementation("org.jetbrains.kotlin:kotlin-test")
    testFixturesImplementation(project(":ontrack-test-utils"))
    testFixturesImplementation(testFixtures(project(":ontrack-it-utils")))
    testFixturesImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testFixturesImplementation(testFixtures(project(":ontrack-extension-api")))
    testFixturesImplementation(testFixtures(project(":ontrack-extension-issues")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))

    testRuntimeOnly("org.springframework.boot:spring-boot-starter-webmvc")
}
