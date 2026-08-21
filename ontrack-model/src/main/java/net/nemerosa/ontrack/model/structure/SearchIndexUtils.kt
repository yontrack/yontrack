package net.nemerosa.ontrack.model.structure

import co.elastic.clients.elasticsearch._types.analysis.TokenChar
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import kotlin.reflect.KProperty1

const val AUTOCOMPLETE_ANALYSER = "autocomplete"
const val AUTOCOMPLETE_SEARCH_ANALYSER = "autocomplete_search"
const val AUTOCOMPLETE_TOKENIZER = "autocomplete_tokenizer"

/**
 * Name of the sub-field holding the unanalyzed value of an autocomplete field, used to
 * recognize a token which matches the whole field value.
 */
const val EXACT_SUB_FIELD = "exact"

/**
 * Boost given to an exact match, so that it always outranks the prefix matches of the
 * autocomplete index.
 */
const val EXACT_MATCH_BOOST = 10.0f

fun CreateIndexRequest.Builder.autoCompleteSettings(): CreateIndexRequest.Builder =
    settings { settings ->
        settings.analysis { analysis ->
            analysis.analyzer(AUTOCOMPLETE_ANALYSER) { analyzer ->
                analyzer
                    .custom { custom ->
                        custom
                            .tokenizer(AUTOCOMPLETE_TOKENIZER)
                            .filter(listOf("lowercase"))
                    }
            }
                .analyzer(AUTOCOMPLETE_SEARCH_ANALYSER) { analyzer ->
                    analyzer
                        .custom { custom ->
                            custom
                                .tokenizer("standard")
                                .filter(listOf("lowercase"))
                        }
                }
                .tokenizer(AUTOCOMPLETE_TOKENIZER) { tokenizer ->
                    tokenizer
                        .definition { definition ->
                            definition
                                .edgeNgram { edgeNgram ->
                                    edgeNgram
                                        .minGram(3)
                                        .maxGram(50)
                                        .tokenChars(
                                            listOf(
                                                TokenChar.Letter,
                                                TokenChar.Digit,
                                                TokenChar.Punctuation,
                                                TokenChar.Whitespace
                                            )
                                        )
                                }
                        }
                }
        }
    }

fun TypeMapping.Builder.id(property: KProperty1<*, Int>): TypeMapping.Builder =
    properties(property.name) { property ->
        property.long_ {
            it.index(false)
        }
    }

fun TypeMapping.Builder.keyword(property: KProperty1<*, String>): TypeMapping.Builder =
    properties(property.name) { property ->
        property.keyword { it }
    }

fun TypeMapping.Builder.keywordAndText(property: KProperty1<*, String>): TypeMapping.Builder =
    properties(property.name) { property ->
        property.keyword { keyword ->
            keyword.fields("text") { field ->
                field.text { it }
            }
        }
    }

fun TypeMapping.Builder.text(property: KProperty1<*, String>): TypeMapping.Builder =
    properties(property.name) { property ->
        property.text { it }
    }

fun TypeMapping.Builder.autoCompleteText(property: KProperty1<*, String>): TypeMapping.Builder =
    properties(property.name) { property ->
        property
            .text { text ->
                text
                    .analyzer(AUTOCOMPLETE_ANALYSER)
                    .searchAnalyzer(AUTOCOMPLETE_SEARCH_ANALYSER)
            }
    }

/**
 * Same as [autoCompleteText], with an additional [EXACT_SUB_FIELD] keyword sub-field holding the
 * whole, unanalyzed value. The autocomplete analyzer indexes prefixes only, so a token matching the
 * complete value scores no better than a token matching a common prefix; the sub-field allows such
 * an exact match to be recognized and boosted, see [exactMatch].
 */
fun TypeMapping.Builder.autoCompleteTextWithExactMatch(property: KProperty1<*, String>): TypeMapping.Builder =
    properties(property.name) { property ->
        property
            .text { text ->
                text
                    .analyzer(AUTOCOMPLETE_ANALYSER)
                    .searchAnalyzer(AUTOCOMPLETE_SEARCH_ANALYSER)
                    .fields(EXACT_SUB_FIELD) { field ->
                        field.keyword { it }
                    }
            }
    }

/**
 * Boosted, case insensitive query on the [EXACT_SUB_FIELD] sub-field of a field mapped with
 * [autoCompleteTextWithExactMatch]. Meant to be used as a `should` clause, next to the
 * query performing the actual search.
 */
fun Query.Builder.exactMatch(property: KProperty1<*, String>, token: String): ObjectBuilder<Query> =
    term { term ->
        term
            .field("${property.name}.$EXACT_SUB_FIELD")
            .value(token)
            .caseInsensitive(true)
            .boost(EXACT_MATCH_BOOST)
    }

fun MultiMatchQuery.Builder.fields(vararg fields: Pair<KProperty1<*, *>, Double?>): MultiMatchQuery.Builder {
    val fieldList = fields.map { (field, boost) ->
        if (boost != null) {
            "${field.name}^$boost"
        } else {
            field.name
        }
    }
    return fields(fieldList)
}