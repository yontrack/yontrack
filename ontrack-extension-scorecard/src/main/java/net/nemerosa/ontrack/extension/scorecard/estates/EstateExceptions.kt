package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.exceptions.NotFoundException

/**
 * No estate with this ID.
 */
class EstateNotFoundException(id: Int) : NotFoundException("Estate not found: %s", id)

/**
 * An estate with this name already exists.
 */
class EstateNameAlreadyExistsException(name: String) : InputException("An estate named %s already exists.", name)

/**
 * The definition of an estate is not valid.
 */
class EstateInputException(message: String) : InputException("%s", message)
