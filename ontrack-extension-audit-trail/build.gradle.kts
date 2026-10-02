plugins {
    `java-library`
}

description = "Audit trail: the hash-chained trail of every build, its endorsements and the evidence of its validations."

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-environments"))
    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-ui-graphql"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("io.micrometer:micrometer-core")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(project(":ontrack-extension-general"))
    testImplementation(project(":ontrack-extension-workflows"))
    testImplementation(testFixtures(project(":ontrack-extension-workflows")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
