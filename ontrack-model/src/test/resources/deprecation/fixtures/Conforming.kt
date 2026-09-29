package fixture

@Deprecated("Removed in V7. Use newThing instead. See #1234")
fun oldThing() = Unit

@Deprecated(
    message = "Removed in V6. " +
        "No replacement. See #1235",
    replaceWith = ReplaceWith("nothing"),
)
@JvmStatic
internal fun <T> List<T>.otherOldThing() = Unit

enum class Levels {
    @Deprecated("Removed in V7. Use HIGH instead. See #1236")
    TOP,
    HIGH,
}

class ConformingTest {
    @Deprecated("Removed in V7. Use newThing instead. See #1234")
    fun `old thing (legacy)`() = Unit
}
