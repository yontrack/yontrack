plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-git"))

    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-extension-casc"))
    implementation("org.apache.commons:commons-lang3")
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-config"))

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-git")))
    testImplementation(testFixtures(project(":ontrack-extension-issues")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}