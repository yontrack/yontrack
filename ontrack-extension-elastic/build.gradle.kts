plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("co.elastic.clients:elasticsearch-java")
    implementation("org.springframework.boot:spring-boot-elasticsearch")
    implementation("io.micrometer:micrometer-core")
    implementation("commons-codec:commons-codec")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(project(":ontrack-extension-general"))
    testImplementation("org.testcontainers:testcontainers")
    // testImplementation(project(path = ":ontrack-extension-api", configuration = "tests"))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
