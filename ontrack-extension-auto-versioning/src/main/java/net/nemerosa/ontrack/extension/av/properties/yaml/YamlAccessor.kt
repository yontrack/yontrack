package net.nemerosa.ontrack.extension.av.properties.yaml

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.properties.support.JsonPropertyAccessor
import net.nemerosa.ontrack.yaml.Yaml
import org.springframework.expression.EvaluationContext
import org.springframework.expression.ExpressionException
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

class YamlAccessor(
    private val content: String,
) {

    private val yaml = Yaml()
    private val root: List<JsonNode> = yaml.read(content)

    private val context: EvaluationContext = StandardEvaluationContext().apply {
        addPropertyAccessor(JsonPropertyAccessor())
    }

    private val parser: SpelExpressionParser = SpelExpressionParser()

    fun getValue(spel: String): Any? =
        if (content.isBlank()) {
            throw YamlNoContentException(spel)
        } else {
            try {
                parser.parseExpression(spel).getValue(context, root)
            } catch (ex: ExpressionException) {
                throw YamlEvaluationException(spel, ex)
            }
        }

    fun setValue(spel: String, value: Any) {
        if (content.isBlank()) {
            throw YamlNoContentException(spel)
        } else {
            try {
                parser.parseExpression(spel).setValue(context, root, value)
            } catch (ex: ExpressionException) {
                throw YamlEvaluationException(spel, ex)
            }
        }
    }

    fun write(): String = yaml.write(root)
}