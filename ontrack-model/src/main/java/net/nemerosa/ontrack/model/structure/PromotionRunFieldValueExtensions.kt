package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.node.StringNode

fun fieldValue(name: String, value: String) = PromotionRunFieldValue(name, StringNode(value))
