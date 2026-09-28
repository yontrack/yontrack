plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-general"))
    implementation("io.micrometer:micrometer-core")
    implementation("org.slf4j:slf4j-api")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(project(":ontrack-extension-general"))
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
