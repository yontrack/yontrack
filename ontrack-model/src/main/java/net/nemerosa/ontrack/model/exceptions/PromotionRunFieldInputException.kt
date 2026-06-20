package net.nemerosa.ontrack.model.exceptions

open class PromotionRunFieldInputException(message: String) : InputException(message)

class PromotionRunFieldRequiredException(fieldName: String) : PromotionRunFieldInputException(
    "Field '$fieldName' is required when creating a promotion run."
)

class PromotionRunFieldTypeException(fieldName: String, expectedType: String) : PromotionRunFieldInputException(
    "Field '$fieldName' must be of type $expectedType."
)

class PromotionRunFieldChoiceException(fieldName: String, value: String, options: List<String>) :
    PromotionRunFieldInputException(
        "Value '$value' for field '$fieldName' is not one of the allowed choices: ${options.joinToString()}."
    )
