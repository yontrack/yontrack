package net.nemerosa.ontrack.service.events

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventListener
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import kotlin.test.assertEquals

class EventListenerServiceImplTest {

    private val calls = mutableListOf<String>()

    private inner class First : EventListener {
        override fun onEvent(event: Event) {
            calls += "first"
        }
    }

    private inner class Second : EventListener {
        override fun onEvent(event: Event) {
            calls += "second"
        }
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    private inner class Prioritized : EventListener {
        override fun onEvent(event: Event) {
            calls += "ordered"
        }
    }

    @Test
    fun `Listeners are called in their order, those without one in the order of their registration`() {
        val context = mockk<ApplicationContext>()
        every { context.getBeansOfType(EventListener::class.java) } returns linkedMapOf(
            "first" to First(),
            "second" to Second(),
            "ordered" to Prioritized(),
        )
        val service = EventListenerServiceImpl(context, SimpleMeterRegistry())

        service.onEvent(mockk())

        assertEquals(listOf("ordered", "first", "second"), calls)
    }
}
