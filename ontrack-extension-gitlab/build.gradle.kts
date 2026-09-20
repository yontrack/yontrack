plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-git"))

    implementation(project(":ontrack-extension-scm"))
    implementation("org.apache.commons:commons-lang3")
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-config"))
    implementation(project(":ontrack-extension-notifications"))
    implementation(project(":ontrack-extension-auto-versioning"))

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(project(":ontrack-extension-jenkins"))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.yaml:snakeyaml")
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-issues")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
