package net.nemerosa.ontrack.service;

import tools.jackson.databind.JsonNode;

import java.util.Optional;

public abstract class AbstractPredefinedBuildFilterProvider extends AbstractBuildFilterProvider<Object> {

    @Override
    public boolean isPredefined() {
        return true;
    }

    @Override
    public Optional<Object> parse(JsonNode data) {
        return Optional.of(new Object());
    }

}
