package fixture

class Warning(private val deprecationService: DeprecationService) {
    fun old() {
        deprecationService.deprecatedUsage(
            surface = DeprecationSurface.REST,
            item = "POST /rest/old",
            message = "Removed in V7. Use PUT /rest/new instead. See #1234",
        )
    }

    fun generic(name: String) {
        deprecationService.deprecatedUsage(
            surface = DeprecationSurface.CONFIG,
            item = "ontrack.config.$name",
            message = "Removed in V7. No replacement. See #1234",
        )
    }
}
