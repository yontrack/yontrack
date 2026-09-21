package net.nemerosa.ontrack.json;

import tools.jackson.databind.JsonNode;

public interface JsonBuilder<J extends JsonNode> {

    J end();

}
