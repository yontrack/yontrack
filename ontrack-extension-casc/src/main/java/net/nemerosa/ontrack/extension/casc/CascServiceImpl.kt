package net.nemerosa.ontrack.extension.casc

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.casc.context.OntrackContext
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.merge
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.dataformat.yaml.YAMLMapper

@Service
@Transactional
class CascServiceImpl(
    private val ontrackContext: OntrackContext,
    private val securityService: SecurityService,
    private val preprocessors: List<CascPreprocessor>,
) : CascService {

    private val mapper = YAMLMapper.builder().configureForJackson2().build()

    override fun runYaml(yaml: List<String>) {
        // Parsing each YAML content
        val parsed = yaml.map {
            it.parse()
        }
        // Running
        run(parsed)
    }

    override fun renderAsJson(): JsonNode {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        return securityService.asAdmin {
            mapOf(
                ROOT to ontrackContext.render()
            ).asJson()
        }
    }

    override fun renderAsYaml(): String = mapper.writeValueAsString(
        renderAsJson()
    )

    private fun run(nodes: List<JsonNode>) {
        val merged = nodes.fold(NullNode.instance as JsonNode) { acc, r ->
            acc.merge(r)
        }
        run(merged)
    }

    private fun run(node: JsonNode) {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        // We want the root to be `ontrack`
        val ontrack = node.path(ROOT)
        if (ontrack.isNull || !ontrack.isObject) {
            error("Root of the Ontrack CasC must be `$ROOT`.")
        } else {
            // Starts running the CasC
            securityService.asAdmin {
                ontrackContext.run(ontrack as ObjectNode, listOf(ROOT))
            }
        }
    }

    private fun String.parse(): JsonNode =
        mapper.readTree(preprocess())

    private fun String.preprocess(): String =
        preprocessors.fold(this) { acc, preprocessor -> preprocessor.process(acc) }

    companion object {
        const val ROOT = "ontrack"
    }

}