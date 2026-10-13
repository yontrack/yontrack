package net.nemerosa.ontrack.service.job

import org.quartz.CronExpression

/**
 * Conversion of the cron expressions of the job schedules, written in the syntax of Spring
 * (`org.springframework.scheduling.support.CronExpression`), into the syntax of Quartz.
 *
 * The two differ on the days of the week and on how the day of the month combines with them:
 *
 * - Spring numbers the days of the week from Monday (1) to Sunday (7, or 0); Quartz from Sunday (1)
 *   to Saturday (7). The days are converted one by one, and the ranges and steps are expanded the
 *   way Spring expands them - `*&#47;2` is Monday, Wednesday, Friday and Sunday in Spring.
 * - Quartz needs `?` for either the day of the month or the day of the week: an expression
 *   restricting both is refused.
 * - The macros of Spring (`@daily`...) are replaced by their expressions.
 */
object QuartzCron {

    private val macros = mapOf(
        "@yearly" to "0 0 0 1 1 *",
        "@annually" to "0 0 0 1 1 *",
        "@monthly" to "0 0 0 1 * *",
        "@weekly" to "0 0 0 * * 0",
        "@daily" to "0 0 0 * * *",
        "@midnight" to "0 0 0 * * *",
        "@hourly" to "0 0 * * * *",
    )

    /**
     * Names of the days of the week, in the order of Spring, from Monday (1)
     */
    private val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private val allDays = (1..7).toSet()

    /**
     * Converts a Spring cron expression into a Quartz one.
     *
     * @throws IllegalArgumentException If the expression cannot be converted
     */
    fun of(spring: String): String {
        val expression = macros[spring.trim().lowercase()] ?: spring.trim()
        val fields = expression.split(Regex("\\s+"))
        require(fields.size == 6) { "A cron expression has six fields: $spring" }
        val (second, minute, hour) = fields
        val dayOfMonth = fields[3].takeIf { it != "?" } ?: "*"
        val month = fields[4]
        val dayOfWeek = daysOfWeek(fields[5].uppercase())
        val quartz = when {
            dayOfWeek == null -> "$second $minute $hour $dayOfMonth $month ?"
            dayOfMonth == "*" -> "$second $minute $hour ? $month $dayOfWeek"
            else -> throw IllegalArgumentException(
                "A cron expression cannot restrict both the day of the month and the day of the week: $spring"
            )
        }
        require(CronExpression.isValidExpression(quartz)) {
            "The cron expression $spring cannot be converted: $quartz is not valid"
        }
        return quartz
    }

    /**
     * Days of the week in the Quartz syntax, or `null` when every day is selected.
     */
    private fun daysOfWeek(field: String): String? {
        if (field == "*" || field == "?") return null
        val tokens = field.split(",")
        // Last day or nth day of the month: one day at a time
        if (tokens.any { it.contains('L') || it.contains('#') }) {
            return tokens.joinToString(",") { token ->
                when {
                    token.endsWith("L") -> "${quartzDay(token.removeSuffix("L"))}L"
                    token.contains('#') -> "${quartzDay(token.substringBefore('#'))}#${token.substringAfter('#')}"
                    else -> expand(token).sorted().joinToString(",") { quartzDay(it) }
                }
            }
        }
        val selected = tokens.flatMap { expand(it) }.toSet()
        return if (selected == allDays) {
            null
        } else {
            selected.map { quartzDay(it) }.sortedBy { it.toInt() }.joinToString(",")
        }
    }

    /**
     * Days, from Monday (1) to Sunday (7), selected by a token of a Spring field.
     */
    private fun expand(token: String): Set<Int> {
        val (range, step) = if (token.contains('/')) {
            token.substringBefore('/') to token.substringAfter('/').toInt()
        } else {
            token to 1
        }
        val (from, to) = when {
            range == "*" -> 1 to 7
            range.contains('-') -> springDay(range.substringBefore('-')) to springDay(range.substringAfter('-'))
            step > 1 -> springDay(range) to 7
            else -> springDay(range).let { it to it }
        }
        require(from <= to) { "Invalid range of days: $token" }
        return (from..to step step).map { if (it == 0) 7 else it }.toSet()
    }

    /**
     * Day of the week of Spring, from Monday (1) to Sunday (7 or 0), by number or name.
     */
    private fun springDay(value: String): Int {
        val index = days.indexOf(value)
        return if (index >= 0) index + 1 else value.toInt().also {
            require(it in 0..7) { "Invalid day of the week: $value" }
        }
    }

    /**
     * Day of the week of Quartz, from Sunday (1) to Saturday (7), for a day of Spring.
     */
    private fun quartzDay(value: String): String = quartzDay(springDay(value))

    private fun quartzDay(day: Int): String = ((day % 7) + 1).toString()
}
