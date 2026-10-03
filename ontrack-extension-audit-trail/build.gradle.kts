plugins {
    `java-library`
}

description = "Audit trail: the hash-chained trail of every build, its endorsements and the evidence of its validations."

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-environments"))
    implementation(project(":ontrack-extension-license"))
    implementation(project(":ontrack-repository-support"))
    implementation(project(":ontrack-ui-graphql"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("io.micrometer:micrometer-core")

    // Evidence storage (#1963): S3-compatible, through the AWS SDK v2, which the Spring Boot BOM does
    // not manage. The synchronous client only, on the Apache HttpClient 5 transport: the Netty one is
    // for the asynchronous client, which nothing uses, and the HttpClient 4 one is legacy.
    implementation(platform("software.amazon.awssdk:bom:2.55.11"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:apache5-client")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(project(":ontrack-extension-general"))
    testImplementation(project(":ontrack-extension-workflows"))
    testImplementation(testFixtures(project(":ontrack-extension-workflows")))
    testImplementation(testFixtures(project(":ontrack-extension-queue")))
    testImplementation(project(":ontrack-extension-notifications"))
    testImplementation(testFixtures(project(":ontrack-extension-notifications")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
