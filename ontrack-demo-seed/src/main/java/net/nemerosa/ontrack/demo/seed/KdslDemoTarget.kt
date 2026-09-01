package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.dashboards.DashboardWidget
import net.nemerosa.ontrack.kdsl.spec.dashboards.DashboardWidgetLayout
import net.nemerosa.ontrack.kdsl.spec.dashboards.dashboards
import net.nemerosa.ontrack.kdsl.spec.dashboards.deleteDashboard
import net.nemerosa.ontrack.kdsl.spec.dashboards.saveDashboard
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.DashboardContextUserScope
import net.nemerosa.ontrack.kdsl.spec.extension.environments.Environment
import net.nemerosa.ontrack.kdsl.spec.extension.environments.Slot
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.setProperty
import java.time.LocalDateTime

/**
 * [DemoTarget] against a real Yontrack instance, through the KDSL.
 *
 * Thin on purpose: every decision about what the demo contains lives in [DemoContent] and
 * [DemoSeed], so that both can be tested without a server.
 */
class KdslDemoTarget(private val ontrack: Ontrack) : DemoTarget {

    override fun projects(): List<DemoProject> = ontrack.projects().map(::KdslDemoProject)

    override fun createProject(name: String, description: String): DemoProject =
        KdslDemoProject(ontrack.createProject(name, description))

    override fun environments(): List<DemoEnvironment> =
        ontrack.environments.list().map(::KdslDemoEnvironment)

    override fun createEnvironment(
        name: String,
        order: Int,
        description: String,
        tags: List<String>,
    ): DemoEnvironment = KdslDemoEnvironment(
        ontrack.environments.createEnvironment(
            name = name,
            order = order,
            description = description,
            tags = tags,
        )
    )

    override fun dashboards(): List<DemoDashboardHandle> =
        ontrack.dashboards()
            // The built-in dashboard cannot be deleted, and there is nothing to reset
            // about it: it is the same on every instance.
            .filter { it.userScope != DashboardContextUserScope.BUILT_IN }
            .map { KdslDemoDashboardHandle(ontrack, it.uuid, it.name) }

    override fun saveDashboard(dashboard: DemoDashboard) {
        ontrack.saveDashboard(
            uuid = dashboard.uuid,
            name = dashboard.name,
            widgets = dashboard.widgets.map {
                DashboardWidget(
                    uuid = it.uuid,
                    key = it.key,
                    config = it.config,
                    layout = DashboardWidgetLayout(
                        x = it.layout.x,
                        y = it.layout.y,
                        w = it.layout.w,
                        h = it.layout.h,
                    ),
                )
            },
        )
    }

    companion object {
        /**
         * The release property carries the version a build shows as its display name.
         */
        const val RELEASE_PROPERTY = "net.nemerosa.ontrack.extension.general.ReleasePropertyType"
    }
}

private class KdslDemoDashboardHandle(
    private val ontrack: Ontrack,
    private val uuid: String,
    override val name: String,
) : DemoDashboardHandle {

    override fun delete() = ontrack.deleteDashboard(uuid)
}

private class KdslDemoProject(val project: Project) : DemoProject {

    override val name: String get() = project.name

    override fun delete() = project.delete()

    override fun createBranch(name: String, description: String): DemoBranch =
        KdslDemoBranch(project.createBranch(name, description))
}

private class KdslDemoBranch(val branch: Branch) : DemoBranch {

    override val name: String get() = branch.name

    override fun createPromotionLevel(name: String, description: String) {
        branch.createPromotionLevel(name, description)
    }

    override fun createValidationStamp(name: String, description: String) {
        branch.createValidationStamp(name, description)
    }

    override fun createBuild(name: String, description: String, creation: LocalDateTime): DemoBuild {
        // Yontrack stamps a build with the time it is created, so the demo's history has
        // to be backdated in a second call.
        val build = branch.createBuild(name, description).updateCreationTime(creation)
        return KdslDemoBuild(build)
    }
}

private class KdslDemoBuild(val build: Build) : DemoBuild {

    override val name: String get() = build.name

    override fun setRelease(release: String) {
        build.setProperty(KdslDemoTarget.RELEASE_PROPERTY, mapOf("name" to release))
    }

    override fun promote(promotionLevel: String, description: String, at: LocalDateTime) {
        build.promote(promotionLevel, description, at)
    }

    override fun validate(validationStamp: String, status: ValidationStatus, description: String) {
        build.validate(validationStamp, status.name, description)
    }

    override fun linkTo(build: DemoBuild) {
        this.build.linkTo((build as KdslDemoBuild).build)
    }
}

private class KdslDemoEnvironment(val environment: Environment) : DemoEnvironment {

    override val name: String get() = environment.name

    override fun delete() = environment.delete()

    override fun createSlot(project: DemoProject, description: String): DemoSlot =
        KdslDemoSlot(
            environment.createSlot(
                project = (project as KdslDemoProject).project,
                description = description,
            )
        )
}

private class KdslDemoSlot(val slot: Slot) : DemoSlot {

    /**
     * Runs the pipeline all the way through, so the slot shows a deployed build rather
     * than one waiting for something to happen to it.
     */
    override fun deploy(build: DemoBuild) {
        slot.createPipeline((build as KdslDemoBuild).build)
            .startDeploying()
            .finishDeployment()
    }
}
