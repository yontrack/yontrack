package net.nemerosa.ontrack.extension.scm;

import net.nemerosa.ontrack.model.exceptions.NotFoundException;

public class SCMFileChangeFilterNotFound extends NotFoundException {
    public SCMFileChangeFilterNotFound(String name) {
        super("Change log file filter with name %s cannot be found.", name);
    }
}
