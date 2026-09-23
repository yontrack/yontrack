plugins {
    `java-library`
}

description = "Security findings: findings, their observations and their exposure on branches."

dependencies {
    api(project(":ontrack-extension-support"))
    api(project(":ontrack-extension-general"))

    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-ui-graphql"))

    testImplementation(project(":ontrack-extension-chart"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation(testFixtures(project(":ontrack-extension-notifications")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))
    testImplementation("com.networknt:json-schema-validator")

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
