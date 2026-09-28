plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-extension-auto-versioning"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-notifications"))
    implementation(project(":ontrack-extension-config"))

    implementation("org.apache.commons:commons-lang3")

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(project(":ontrack-extension-git"))
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-git")))
    testImplementation(testFixtures(project(":ontrack-extension-issues")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
