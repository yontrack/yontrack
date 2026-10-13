plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-model"))
    // The JOBS table is keyed by the job keys
    api(project(":ontrack-job"))
}