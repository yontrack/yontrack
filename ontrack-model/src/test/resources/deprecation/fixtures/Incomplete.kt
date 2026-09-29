package fixture

class Incomplete {
    @Deprecated("Removed in V7. Use other instead.")
    val noIssue: String = ""

    @Deprecated("Removed in V7. See #1234")
    fun noReplacement() = Unit
}
