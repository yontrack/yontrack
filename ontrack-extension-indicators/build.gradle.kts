plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-support"))
    api(project(":ontrack-ui-graphql"))

    implementation(project(":ontrack-repository-support"))
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.opencsv:opencsv")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
