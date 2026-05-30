import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node")
}

configure<NodeExtension> {
    version.set("23.7.0")
    npmVersion.set("10.9.2")
    download.set(true)
}

val webBuild by tasks.registering(NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
}

val test by tasks.registering(NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "test"))
}

tasks.named("build") {
    // dependsOn(webBuild)
    dependsOn(test)
}

// Docker image

val dockerBuild by tasks.registering(Exec::class) {
    dependsOn(test)
    workingDir = projectDir
    commandLine("sh", "-c", """
        docker image build -t nemerosa/ontrack-ui:${project.version} . && \
        docker image tag nemerosa/ontrack-ui:${project.version} nemerosa/ontrack-ui:latest && \
        docker image tag nemerosa/ontrack-ui:${project.version} yontrack/yontrack-ui:${project.version} && \
        docker image tag nemerosa/ontrack-ui:${project.version} yontrack/yontrack-ui:latest
    """)
}
