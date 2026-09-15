plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-git"))

    // Used for the migration of Stash configurations which are actually Bitbucket Cloud configurations
    implementation(project(":ontrack-extension-stash"))

    implementation("org.apache.commons:commons-lang3")
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-config"))

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(project(":ontrack-extension-jenkins"))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation(testFixtures(project(":ontrack-extension-git")))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("com.networknt:json-schema-validator")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    // testImplementation(project(path = ":ontrack-extension-issues", configuration = "tests"))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}