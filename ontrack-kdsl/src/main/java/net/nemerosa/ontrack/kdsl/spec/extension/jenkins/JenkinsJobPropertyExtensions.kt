package net.nemerosa.ontrack.kdsl.spec.extension.jenkins

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.spec.*

const val JENKINS_JOB_PROPERTY = "net.nemerosa.ontrack.extension.jenkins.JenkinsJobPropertyType"

var Project.jenkinsJob: JenkinsJobProperty?
    get() = getProperty(JENKINS_JOB_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(JENKINS_JOB_PROPERTY, value)
        } else {
            deleteProperty(JENKINS_JOB_PROPERTY)
        }
    }

var Branch.jenkinsJob: JenkinsJobProperty?
    get() = getProperty(JENKINS_JOB_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(JENKINS_JOB_PROPERTY, value)
        } else {
            deleteProperty(JENKINS_JOB_PROPERTY)
        }
    }

var PromotionLevel.jenkinsJob: JenkinsJobProperty?
    get() = getProperty(JENKINS_JOB_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(JENKINS_JOB_PROPERTY, value)
        } else {
            deleteProperty(JENKINS_JOB_PROPERTY)
        }
    }

var ValidationStamp.jenkinsJob: JenkinsJobProperty?
    get() = getProperty(JENKINS_JOB_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(JENKINS_JOB_PROPERTY, value)
        } else {
            deleteProperty(JENKINS_JOB_PROPERTY)
        }
    }

@JsonDeserialize(using = JenkinsJobPropertyDeserializer::class)
data class JenkinsJobProperty(
    val configuration: String,
    val job: String,
    /**
     * This property is filled in only on read.
     */
    val url: String? = null,
)

class JenkinsJobPropertyDeserializer : ValueDeserializer<JenkinsJobProperty>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JenkinsJobProperty {
        val node: JsonNode = p.readValueAsTree()
        return JenkinsJobProperty(
            configuration = node.path("configuration").path("name").asText(),
            job = node.path("job").asText(),
            url = node.path("url").asText(),
        )
    }

}