plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-git"))

    implementation("org.apache.commons:commons-lang3")
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-casc"))

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.yaml:snakeyaml")
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-issues")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
