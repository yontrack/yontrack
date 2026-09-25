package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.support.UITest
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.SearchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties = [
        "ontrack.config.search.index.immediate=true"
    ]
)
@UITest
@AsAdminTest
abstract class AbstractSearchTestSupport : AbstractQLKTITSupport() {

    @Autowired
    protected lateinit var searchService: SearchService

    protected fun Build.release(value: String) {
        setProperty(this, ReleasePropertyType::class.java, ReleaseProperty(value))
    }

}
