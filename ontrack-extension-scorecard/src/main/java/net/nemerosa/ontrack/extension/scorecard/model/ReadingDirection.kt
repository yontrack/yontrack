package net.nemerosa.ontrack.extension.scorecard.model

/**
 * Which way a reading is better. It is fixed by the reading, and it says how a target judges it.
 */
enum class ReadingDirection {

    /**
     * The lower the better: the target is met when the value is lower than or equal to it
     */
    LOWER_IS_BETTER,

    /**
     * The higher the better: the target is met when the value is higher than or equal to it
     */
    HIGHER_IS_BETTER;

    /**
     * Judges a value against a target: met or missed, `null` when it cannot be judged — no value
     * (the reading is unknown) or no target (the reading is shown, not judged).
     */
    fun met(value: Double?, target: Double?): Boolean? =
        if (value == null || target == null) {
            null
        } else when (this) {
            LOWER_IS_BETTER -> value <= target
            HIGHER_IS_BETTER -> value >= target
        }
}
