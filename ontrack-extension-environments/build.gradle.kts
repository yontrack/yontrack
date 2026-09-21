plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-notifications"))
    implementation(project(":ontrack-extension-workflows"))
    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-repository"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-extension-config"))

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(project(":ontrack-extension-scm"))
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-notifications")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))
    testImplementation(testFixtures(project(":ontrack-extension-workflows")))
    testImplementation(testFixtures(project(":ontrack-extension-license")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
