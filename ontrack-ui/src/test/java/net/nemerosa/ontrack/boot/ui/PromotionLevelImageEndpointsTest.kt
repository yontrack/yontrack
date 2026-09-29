package net.nemerosa.ontrack.boot.ui

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.nemerosa.ontrack.common.Document
import net.nemerosa.ontrack.model.settings.PredefinedPromotionLevelService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The image of a promotion level, and of a predefined promotion level, is read with `GET` and set
 * with `PUT` and a Base64 body. The multipart `POST` was removed in V6 (#1922).
 */
class PromotionLevelImageEndpointsTest {

    private val png = byteArrayOf(1, 2, 3, 4)

    @Test
    fun `The image of a promotion level is read with GET and set with PUT only`() {
        assertEquals(
            setOf(RequestMethod.GET, RequestMethod.PUT),
            imageMethods(PromotionLevelController::class, "promotionLevels/{promotionLevelId}/image"),
        )
    }

    @Test
    fun `The image of a predefined promotion level is read with GET and set with PUT only`() {
        assertEquals(
            setOf(RequestMethod.GET, RequestMethod.PUT),
            imageMethods(
                PredefinedPromotionLevelController::class,
                "predefinedPromotionLevels/{predefinedPromotionLevelId}/image"
            ),
        )
    }

    @Test
    fun `PUT of a promotion level image decodes its Base64 body as a PNG`() {
        val structureService = mockk<StructureService>()
        val document = slot<Document>()
        every { structureService.setPromotionLevelImage(ID.of(10), capture(document)) } returns Unit

        PromotionLevelController(structureService)
            .putPromotionLevelImage(ID.of(10), Base64.getEncoder().encodeToString(png))

        assertEquals("image/png", document.captured.type)
        assertContentEquals(png, document.captured.content)
    }

    @Test
    fun `PUT of a predefined promotion level image decodes its Base64 body as a PNG`() {
        val service = mockk<PredefinedPromotionLevelService>()
        val document = slot<Document>()
        every { service.setPredefinedPromotionLevelImage(ID.of(10), capture(document)) } returns Unit

        PredefinedPromotionLevelController(service)
            .putPredefinedPromotionLevelImage(ID.of(10), Base64.getEncoder().encodeToString(png))

        assertEquals("image/png", document.captured.type)
        assertContentEquals(png, document.captured.content)
    }

    private fun imageMethods(controller: KClass<*>, path: String): Set<RequestMethod> =
        controller.java.declaredMethods
            .mapNotNull { AnnotatedElementUtils.findMergedAnnotation(it, RequestMapping::class.java) }
            .filter { mapping -> path in mapping.path || path in mapping.value }
            .flatMap { it.method.toList() }
            .toSet()
}
