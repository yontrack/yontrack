package net.nemerosa.ontrack.extension.queue.search

import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.queue.dispatching.QueuePostCommitDispatcher
import net.nemerosa.ontrack.model.events.EventFactoryImpl
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.junit.jupiter.api.Test

class SearchRenameEventListenerTest {

    private val dispatcher = mockk<QueuePostCommitDispatcher>(relaxed = true)
    private val processor = mockk<SearchRenameQueueProcessor>()
    private val listener = SearchRenameEventListener(dispatcher, processor)
    private val events = EventFactoryImpl()

    private val project = Project.of(NameDescription.nd("renamed", "")).withId(ID.of(10))
    private val branch = Branch.of(project, NameDescription.nd("main", "")).withId(ID.of(20))

    @Test
    fun `A renamed project queues the re-indexation of the project`() {
        listener.onEvent(events.updateProject(project, previousName = "former"))
        verify(exactly = 1) {
            dispatcher.dispatchAfterCommit(processor, SearchRenameQueuePayload(ProjectEntityType.PROJECT, 10))
        }
    }

    @Test
    fun `A renamed branch queues the re-indexation of the branch`() {
        listener.onEvent(events.updateBranch(branch, previousName = "former"))
        verify(exactly = 1) {
            dispatcher.dispatchAfterCommit(processor, SearchRenameQueuePayload(ProjectEntityType.BRANCH, 20))
        }
    }

    @Test
    fun `An update which does not rename queues nothing`() {
        listener.onEvent(events.updateProject(project))
        listener.onEvent(events.updateBranch(branch))
        verify(exactly = 0) {
            dispatcher.dispatchAfterCommit(any<SearchRenameQueueProcessor>(), any())
        }
    }
}
