package net.nemerosa.ontrack.extension.sonarqube.measures

import net.nemerosa.ontrack.extension.sonarqube.property.SonarQubeProperty
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project

interface SonarQubeMeasuresCollectionService {

    fun collect(project: Project, logger: (String) -> Unit)

    /**
     * Checks if the [build] is eligible for SonarQube collection.
     */
    fun matches(build: Build, property: SonarQubeProperty): Boolean

    /**
     * Gets the measures of the last build scanned by SonarQube.
     *
     * @param branch Branch
     * @return Measures for the last build, if any
     */
    fun getLastMeasures(branch: Branch): SonarQubeMeasures?

    /**
     * Collects SonarQube metrics for an individual build
     *
     * @param build Build to scan
     * @param property Project SonarQube property
     * @return OK or error message
     */
    fun collect(build: Build, property: SonarQubeProperty): SonarQubeMeasuresCollectionResult

    /**
     * Gets the SonarQube measures for a build.
     *
     * @param build Build to get the measures for
     * @return Existing measures or `null` if not found
     */
    fun getMeasures(build: Build): SonarQubeMeasures?

}

/**
 * Reason of the system acting as administrator to collect SonarQube measures.
 */
const val SONARQUBE_SYSTEM_REASON = "sonarqube"
