package net.nemerosa.ontrack.boot.ui

import jakarta.validation.Valid
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.Build.Companion.of
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/rest/structure")
class BuildController(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val securityService: SecurityService,
) {

    @PostMapping("branches/{branchId}/builds/create")
    fun newBuild(@PathVariable branchId: ID, @RequestBody request: @Valid BuildRequest?): ResponseEntity<Build> {
        // Gets the holding branch
        val branch = structureService.getBranch(branchId)
        // Build signature
        val signature = securityService.currentSignature
        // Creates a new build
        var build = of(branch, request!!.asNameDescription(), signature)
        // Saves it into the repository
        build = structureService.newBuild(build)
        // Saves the properties
        for ((propertyTypeName, propertyData) in request.properties) {
            propertyService.editProperty(
                build,
                propertyTypeName,
                propertyData
            )
        }
        // OK
        return ResponseEntity.ok(build)
    }

    /**
     * Looking for a build using its exact name on a branch.
     */
    @GetMapping("branches/{branchId}/builds/{name:[A-Za-z0-9_.-]+}")
    fun getBuildByBranchAndName(@PathVariable branchId: ID, @PathVariable name: String): ResponseEntity<Build> {
        // Gets the holding branch
        val (_, name1, _, _, project) = structureService.getBranch(branchId)
        // Gets the build by name
        return ResponseEntity.ok(
            structureService.findBuildByName(project.name, name1, name)
                .orElseThrow { BuildNotFoundException(project.name, name1, name) }
        )
    }

    @PutMapping("builds/{buildId}/update")
    fun updateBuild(
        @PathVariable buildId: ID,
        @RequestBody nameDescription: @Valid NameDescription
    ): ResponseEntity<Build> {
        // Gets from the repository
        var build = structureService.getBuild(buildId)
        // Updates
        build = build.update(nameDescription)
        // Saves in repository
        structureService.saveBuild(build)
        // As resource
        return ResponseEntity.ok(build)
    }

    /**
     * Update the build signature
     */
    @PutMapping("builds/{buildId}/signature")
    fun updateBuildSignature(@PathVariable buildId: ID, @RequestBody request: SignatureRequest): ResponseEntity<Build> {
        // Gets from the repository
        var build = structureService.getBuild(buildId)
        // Updates
        build = build.withSignature(
            request.getSignature(build.signature)
        )
        // Saves in repository
        structureService.saveBuild(build)
        // As resource
        return ResponseEntity.ok(build)
    }

    @DeleteMapping("builds/{buildId}")
    fun deleteBuild(@PathVariable buildId: ID): ResponseEntity<Ack> {
        return ResponseEntity.ok(structureService.deleteBuild(buildId))
    }

    @GetMapping("builds/{buildId}")
    fun getBuild(@PathVariable buildId: ID): ResponseEntity<Build> {
        return ResponseEntity.ok(structureService.getBuild(buildId))
    }

    /**
     * Gets the previous build
     */
    @GetMapping("builds/{buildId}/previous")
    fun getPreviousBuild(@PathVariable buildId: ID): ResponseEntity<Build> {
        val previousBuild = structureService.getPreviousBuild(buildId)
        return if (previousBuild != null) {
            ResponseEntity.ok(previousBuild)
        } else {
            ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(null)
        }
    }

    /**
     * Gets the next build
     */
    @GetMapping("builds/{buildId}/next")
    fun getNextBuild(@PathVariable buildId: ID): ResponseEntity<Build> {
        val nextBuild = structureService.getNextBuild(buildId)
        return if (nextBuild != null) {
            ResponseEntity.ok(nextBuild)
        } else {
            ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(null)
        }
    }

    /**
     * Gets the build links FROM this build.
     *
     * @param buildId Build to get the links from
     * @return List of builds
     */
    @GetMapping("builds/{buildId}/links/from")
    fun getBuildLinksFrom(
        @PathVariable buildId: ID,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<List<Build>> {
        return ResponseEntity.ok(
            structureService.getBuildDependencyLinks(
                structureService.getBuild(buildId),
                offset,
                size
            ).pageItems.map { it.build },
        )
    }

    /**
     * Gets the build links TO this build.
     *
     * @param buildId Build to get the links to
     * @return List of builds
     */
    @GetMapping("builds/{buildId}/links/to")
    fun getBuildLinksTo(
        @PathVariable buildId: ID,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<List<Build>> {
        return ResponseEntity.ok(
            structureService.getBuildConsumerLinks(
                structureService.getBuild(buildId),
                offset,
                size
            ).pageItems.map { it.build },
        )
    }

    /**
     * Create a link between a build and another, using a form.
     *
     * @param buildId From this build...
     * @return List of builds
     */
    @PutMapping("builds/{buildId}/links/edit")
    fun createBuildLinkFromForm(@PathVariable buildId: ID, @RequestBody form: BuildLinkForm?): ResponseEntity<Build> {
        val build = structureService.getBuild(buildId)
        structureService.editBuildLinks(build, form ?: BuildLinkForm())
        return ResponseEntity.ok(build)
    }

    /**
     * Creates a link between a build and another
     *
     * @param buildId       From this build...
     * @param targetBuildId ... to this build
     * @return List of builds
     */
    @PutMapping("builds/{buildId}/links/{targetBuildId}")
    fun addBuildLink(@PathVariable buildId: ID, @PathVariable targetBuildId: ID): ResponseEntity<Build> {
        val build = structureService.getBuild(buildId)
        val targetBuild = structureService.getBuild(targetBuildId)
        structureService.createBuildLink(build, targetBuild, BuildLink.DEFAULT)
        return ResponseEntity.ok(build)
    }

    /**
     * Deletes a link between a build and another
     *
     * @param buildId       From this build...
     * @param targetBuildId ... to this build
     * @return List of builds
     */
    @DeleteMapping("builds/{buildId}/links/{targetBuildId}")
    fun deleteBuildLink(
        @PathVariable buildId: ID,
        @PathVariable targetBuildId: ID,
        @RequestParam qualifier: String?
    ): ResponseEntity<Build> {
        val build = structureService.getBuild(buildId)
        val targetBuild = structureService.getBuild(targetBuildId)
        structureService.deleteBuildLink(build, targetBuild, qualifier ?: BuildLink.DEFAULT)
        return ResponseEntity.ok(build)
    }
}
