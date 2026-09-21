package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SyncPolicyTest {

    @Test
    fun `Copy sync`() {
        val sources = listOf("a", "b", "c", "d", "e").associateWith { nd(it, it.uppercase()) }
        val targets = listOf("b", "c", "d", "f").associateWith { nd(it, it.lowercase()) }.toMutableMap()

        val result = SyncPolicy.COPY.sync(config(sources, targets))
        assertEquals(2, result.created)
        assertEquals(3, result.presentTargetIgnored)
        assertEquals(0, result.presentTargetReplaced)
        assertEquals(1, result.unknownTargetIgnored)
        assertEquals(0, result.unknownTargetDeleted)

        assertEquals(
                listOf(
                        nd("b", "b"),
                        nd("c", "c"),
                        nd("d", "d"),
                        nd("f", "f"),
                        nd("a", "A"),
                        nd("e", "E")
                ),
                targets.values.toList()
        )
    }

    @Test
    fun sync() {
        val sources = listOf("a", "b", "c", "d", "e").associateWith { nd(it, it.uppercase()) }
        val targets = listOf("b", "c", "d", "f").associateWith { nd(it, it.lowercase()) }.toMutableMap()

        val result = SyncPolicy.SYNC.sync(config(sources, targets))
        assertEquals(2, result.created)
        assertEquals(0, result.presentTargetIgnored)
        assertEquals(3, result.presentTargetReplaced)
        assertEquals(0, result.unknownTargetIgnored)
        assertEquals(1, result.unknownTargetDeleted)

        assertEquals(
                listOf(
                        nd("b", "B"),
                        nd("c", "C"),
                        nd("d", "D"),
                        nd("a", "A"),
                        nd("e", "E")
                ),
                targets.values.toList()
        )

    }

    private fun config(
            sources: Map<String, NameDescription>,
            targets: MutableMap<String, NameDescription>
    ): SyncConfig<NameDescription, String> =
            object : SyncConfig<NameDescription, String> {

                override fun getItemType(): String = "Name description"

                override fun getSourceItems(): Collection<NameDescription> = sources.values

                override fun getTargetItems(): Collection<NameDescription> = targets.values

                override fun getItemId(item: NameDescription): String = item.name

                override fun createTargetItem(source: NameDescription) {
                    targets[source.name] = nd(source.name, source.description)
                }

                override fun replaceTargetItem(source: NameDescription, target: NameDescription) {
                    targets[target.name] = nd(target.name, source.description)
                }

                override fun deleteTargetItem(target: NameDescription) {
                    targets.remove(target.name)
                }

            }

}