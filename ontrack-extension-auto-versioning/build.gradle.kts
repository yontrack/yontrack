plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-repository"))
    implementation(project(":ontrack-ui-graphql"))
    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-extension-general"))
    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-extension-queue"))
    implementation(project(":ontrack-extension-recordings"))
    implementation(project(":ontrack-rabbitmq"))
    implementation("io.micrometer:micrometer-core")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.jayway.jsonpath:json-path")
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-codec:commons-codec")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation(project(":ontrack-extension-notifications"))
    implementation(project(":ontrack-extension-workflows"))
    implementation(project(":ontrack-extension-config"))

    testImplementation(project(":ontrack-extension-general"))
    testImplementation(testFixtures(project(":ontrack-common")))
    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation(testFixtures(project(":ontrack-extension-general")))
    testImplementation(testFixtures(project(":ontrack-extension-notifications")))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))
    testImplementation(testFixtures(project(":ontrack-extension-config")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))
    testImplementation("com.networknt:json-schema-validator")
    testImplementation(testFixtures(project(":ontrack-it-utils")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
