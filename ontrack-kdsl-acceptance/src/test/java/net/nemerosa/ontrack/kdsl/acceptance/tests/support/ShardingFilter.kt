package net.nemerosa.ontrack.kdsl.acceptance.tests.support

import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Splits the acceptance suite across several CI runners.
 *
 * The partition is a *rule computed over the discovered set*, never a list of class names held
 * somewhere: the set of test classes is read back from the discovered tree, sorted, and dealt out
 * round-robin. A new `ACC*` class is therefore always assigned to a shard, where an enumerated
 * list would have to be edited and silently runs nothing when it is not. See
 * `docs/adr/0005-test-suites-are-sharded-by-rule-not-by-list.md`.
 *
 * Dealing the *sorted* names out round-robin rather than hashing them is also a rough balancing
 * heuristic: the names cluster by feature, the cost clusters the same way — the 23 `ACCAutoVersioning*`
 * classes all wait on auto-versioning to complete — so alternating neighbours splits each expensive
 * family evenly instead of leaving its distribution to chance.
 *
 * Registered through `META-INF/services`, and inert unless `shard.total` is greater than 1, so
 * every run that does not ask for a shard — every local run — discovers the whole suite.
 */
class ShardingFilter : PostDiscoveryFilter {

    private val total: Int = intProperty(TOTAL_PROPERTY, 1)
    private val index: Int = intProperty(INDEX_PROPERTY, 1)

    /**
     * The classes this shard owns, per engine root, resolved from the first descriptor of that root
     * the launcher offers.
     *
     * Post-discovery filters are handed one descriptor at a time and never the tree, so the set has
     * to be read back by climbing to the root. The launcher visits the tree depth-first in
     * post-order and only starts removing descriptors as it goes, so the first call still sees the
     * complete tree. Keyed per root because filters are applied to every engine on the classpath
     * and each has to be dealt out over its own classes.
     */
    private val owned = mutableMapOf<UniqueId, Set<String>>()

    override fun apply(descriptor: TestDescriptor): FilterResult {
        if (total <= 1) return FilterResult.included("Not sharded")
        val className = outerClassName(descriptor)
            ?: return FilterResult.included("Not attached to a class")
        val root = rootOf(descriptor)
        val ownedClasses = owned.getOrPut(root.uniqueId) {
            assignShard(classNames(root), total = total, index = index)
        }
        return if (className in ownedClasses) {
            FilterResult.included("Shard $index of $total")
        } else {
            FilterResult.excluded("Not in shard $index of $total")
        }
    }

    private fun classNames(descriptor: TestDescriptor): Set<String> {
        val names = mutableSetOf<String>()
        descriptor.accept { candidate ->
            outerClassName(candidate)?.let { names += it }
        }
        return names
    }

    private fun rootOf(descriptor: TestDescriptor): TestDescriptor {
        var current = descriptor
        while (true) {
            current = current.parent.orElse(null) ?: return current
        }
    }

    companion object {

        const val INDEX_PROPERTY = "shard.index"
        const val TOTAL_PROPERTY = "shard.total"

        /**
         * Deals [classNames] out round-robin over [total] shards and returns the ones belonging to
         * the 1-based [index].
         */
        fun assignShard(classNames: Collection<String>, total: Int, index: Int): Set<String> =
            if (total <= 1) {
                classNames.toSet()
            } else {
                classNames.sorted()
                    .filterIndexed { position, _ -> position % total == index - 1 }
                    .toSet()
            }

        /**
         * Name of the outermost class a descriptor belongs to, or `null` for the engine and any
         * other descriptor with no class behind it.
         *
         * Nested classes answer their outer class so that a class and its nesting always land on
         * the same shard.
         */
        fun outerClassName(descriptor: TestDescriptor): String? {
            var current: TestDescriptor? = descriptor
            while (current != null) {
                val className = when (val source = current.source.orElse(null)) {
                    is MethodSource -> source.className
                    is ClassSource -> source.className
                    else -> null
                }
                if (className != null) return className.substringBefore('$')
                current = current.parent.orElse(null)
            }
            return null
        }

        private fun intProperty(name: String, defaultValue: Int): Int =
            System.getProperty(name)?.toIntOrNull() ?: defaultValue
    }
}
