package net.nemerosa.ontrack.service.templating

enum class UserTemplatingFunctionField {

    /**
     * Username
     */
    @Deprecated("Removed in V6. Use EMAIL instead. See #1920")
    NAME,

    /**
     * Display name
     */
    DISPLAY,

    /**
     * Email
     */
    EMAIL,

}