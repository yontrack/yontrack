plugins {
    `java-library`
}

description = "Security findings: findings, their observations and their exposure on branches."

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-repository-support"))

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-model")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
