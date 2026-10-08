plugins {
    `java-library`
}

description = "Agent governance: the licensed rulings on agents - the promotion condition on assisted builds, the stamp restriction on agent evidence, and the activity of the agents."

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-repository"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-ui-graphql"))

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(project(":ontrack-extension-general"))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
