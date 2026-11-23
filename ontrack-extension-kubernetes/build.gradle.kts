plugins {
    `java-library`
}

description = "Runs work as Kubernetes Jobs in the cluster Yontrack runs in: auto-versioning post-processing."

dependencies {
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-extension-scm"))
    implementation(project(":ontrack-extension-auto-versioning"))

    // Kubernetes client, on the HTTP client of the JDK rather than the default Vert.x one, which
    // would bring Vert.x and Netty into the backend image for a handful of API calls.
    implementation("io.fabric8:kubernetes-client") {
        exclude(group = "io.fabric8", module = "kubernetes-httpclient-vertx")
    }
    implementation("io.fabric8:kubernetes-httpclient-jdk")

    implementation("org.slf4j:slf4j-api")

    testImplementation(project(":ontrack-test-utils"))
    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-extension-scm")))
    testImplementation("io.fabric8:kubernetes-server-mock")

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}
