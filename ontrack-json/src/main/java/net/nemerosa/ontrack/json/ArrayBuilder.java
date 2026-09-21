package net.nemerosa.ontrack.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class ArrayBuilder implements JsonBuilder<ArrayNode> {

    private final JsonNodeFactory factory;
    private final ArrayNode thisNode;

    public ArrayBuilder(JsonNodeFactory factory) {
        this.factory = factory;
        this.thisNode = this.factory.arrayNode();
    }

    public ArrayBuilder with(JsonNode node) {
        thisNode.add(node);
        return this;
    }

    @Override
    public ArrayNode end() {
        return thisNode;
    }
}
