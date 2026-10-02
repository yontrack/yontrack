plugins {
    `java-library`
}

description = "Delivery scorecard: the readings of a project, computed daily from Yontrack's own data."

dependencies {
    api(project(":ontrack-extension-support"))
    api(project(":ontrack-extension-chart"))

    implementation(project(":ontrack-extension-casc"))
    implementation(project(":ontrack-extension-environments"))
    implementation(project(":ontrack-extension-findings"))
    implementation(project(":ontrack-extension-general"))
    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-ui-graphql"))
    implementation("org.apache.commons:commons-math3")
    implementation("io.micrometer:micrometer-core")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    testImplementation("com.networknt:json-schema-validator")

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
