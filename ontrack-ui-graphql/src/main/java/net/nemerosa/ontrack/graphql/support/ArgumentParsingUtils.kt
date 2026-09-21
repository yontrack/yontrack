package net.nemerosa.ontrack.graphql.support

import graphql.schema.DataFetchingEnvironment
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.exceptions.InputException
import kotlin.reflect.KClass

inline fun <reified T : Any> parseArgument(argName: String, env: DataFetchingEnvironment): T =
    parseArgument(T::class, argName, env)

fun <T : Any> parseArgument(type: KClass<T>, argName: String, env: DataFetchingEnvironment): T {
    return parseOptionalArgument(type, argName, env) ?: throw MutationMissingInputException()
}

inline fun <reified T : Any> parseOptionalArgument(argName: String, env: DataFetchingEnvironment): T? =
    parseOptionalArgument(T::class, argName, env)

fun <T : Any> parseOptionalArgument(type: KClass<T>, argName: String, env: DataFetchingEnvironment): T? {
    val input = env.getArgument<Any>(argName) ?: return null
    return input.asJson().parseInto(type)
}

class MutationMissingInputException : InputException("Cannot find any `input` in the mutation input.")
class MutationMissingInputFieldException(fieldName: String) :
    InputException("Cannot find any `input/$fieldName` in the mutation input.")

fun <T> getMutationInputField(env: DataFetchingEnvironment, fieldName: String): T? {
    val input: Map<String, String?> = env.getArgument("input") ?: throw MutationMissingInputException()
    @Suppress("UNCHECKED_CAST")
    return input[fieldName] as? T
}

fun <T> getRequiredMutationInputField(env: DataFetchingEnvironment, fieldName: String): T {
    return getMutationInputField<T>(env, fieldName) ?: throw MutationMissingInputFieldException(fieldName)
}
