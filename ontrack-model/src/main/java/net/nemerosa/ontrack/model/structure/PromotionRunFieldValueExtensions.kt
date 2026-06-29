package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.databind.node.TextNode

fun fieldValue(name: String, value: String) = PromotionRunFieldValue(name, TextNode(value))
