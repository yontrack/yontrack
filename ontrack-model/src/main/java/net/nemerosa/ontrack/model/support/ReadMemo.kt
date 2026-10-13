package net.nemerosa.ontrack.model.support

/**
 * Read memo of a unit of work: what the database gave once in a unit is not asked for again in it
 * (ADR 0021).
 *
 * A unit of work is an HTTP request, opened by the `ReadMemoFilter` of the UI module, or the
 * processing of a queue message, opened around it by its listener. Jobs open none, a run lasting
 * up to hours: they read fresh values. The memo is bound to the thread which opened the unit, so
 * that code running elsewhere - a coroutine on another dispatcher, a task of an executor - finds
 * none, and reads fresh values too.
 *
 * This is no cache: nothing outlives the unit, so that a value written through another replica is
 * read by the next unit of this one.
 *
 * The values are kept by [Region] - settings by type, properties by type and entity - and by key in
 * a region. The code which writes a value in a unit replaces it ([written]) or [evict]s it, so that
 * the unit reads what it wrote.
 *
 * Most callers need the static functions only, which do nothing, or load the value, when no memo
 * is open:
 *
 * ```kotlin
 * private val SETTINGS = ReadMemo.Region<Class<*>, Any>("settings")
 *
 * fun <T : Any> read(type: Class<T>): T = ReadMemo.read(SETTINGS, type) { load(type) } as T
 * ```
 *
 * A unit of work prefetching several values in one query puts them into [current] with [put].
 */
class ReadMemo private constructor() {

    /**
     * Kind of values held by the memo, whose keys are of type [K] and values of type [V], which may
     * be nullable. Two regions are distinct objects: a region is declared once, as a constant.
     *
     * @property name Name of the region, for display only
     */
    class Region<K : Any, V>(val name: String) {
        override fun toString(): String = "ReadMemo.Region($name)"
    }

    private val regions = mutableMapOf<Region<*, *>, MutableMap<Any, Any?>>()

    private fun values(region: Region<*, *>): MutableMap<Any, Any?> =
        regions.getOrPut(region) { mutableMapOf() }

    /**
     * Value of the [key] in the [region], loaded by the [loader] if this memo does not hold it yet,
     * and held from then on. A `null` value is held as well.
     */
    fun <K : Any, V> getOrLoad(region: Region<K, V>, key: K, loader: () -> V): V {
        val values = values(region)
        return if (values.containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            values[key] as V
        } else {
            // Not `getOrPut`: the loader may read this memo itself
            val value = loader()
            values(region)[key] = value
            value
        }
    }

    /**
     * Holds the [value] of the [key] in the [region], replacing any previous one.
     */
    fun <K : Any, V> put(region: Region<K, V>, key: K, value: V) {
        values(region)[key] = value
    }

    /**
     * Whether this memo holds a value, possibly `null`, for the [key] in the [region].
     */
    fun <K : Any> contains(region: Region<K, *>, key: K): Boolean =
        regions[region]?.containsKey(key) == true

    /**
     * Forgets the value of the [key] in the [region], so that the next read loads it again.
     */
    fun <K : Any> remove(region: Region<K, *>, key: K) {
        regions[region]?.remove(key)
    }

    companion object {

        private val current = ThreadLocal<ReadMemo>()

        /**
         * Memo of the unit of work running on this thread, if any.
         */
        @JvmStatic
        fun current(): ReadMemo? = current.get()

        /**
         * Runs the [code] in a unit of work. A unit already open on this thread is joined; otherwise
         * a memo is opened for the [code], and closed when it is over, whether it succeeds or fails.
         */
        @JvmStatic
        fun <T> unitOfWork(code: () -> T): T {
            val existing = current.get()
            return if (existing != null) {
                code()
            } else {
                current.set(ReadMemo())
                try {
                    code()
                } finally {
                    current.remove()
                }
            }
        }

        /**
         * Value of the [key] in the [region]: from the memo of the current unit of work, loading it
         * into it if needed, or from the [loader] alone when no unit of work is open.
         */
        @JvmStatic
        fun <K : Any, V> read(region: Region<K, V>, key: K, loader: () -> V): V {
            val memo = current.get()
            return if (memo != null) {
                memo.getOrLoad(region, key, loader)
            } else {
                loader()
            }
        }

        /**
         * The [value] of the [key] in the [region] has been written: the current unit of work, if
         * any, reads it from now on.
         */
        @JvmStatic
        fun <K : Any, V> written(region: Region<K, V>, key: K, value: V) {
            current.get()?.put(region, key, value)
        }

        /**
         * The value of the [key] in the [region] has changed: the current unit of work, if any,
         * loads it again on its next read.
         */
        @JvmStatic
        fun <K : Any> evict(region: Region<K, *>, key: K) {
            current.get()?.remove(region, key)
        }
    }
}
