package net.nemerosa.ontrack.extension.scm.service

/**
 * Marks a [property type][net.nemerosa.ontrack.model.structure.PropertyType] as holding the commit of
 * a build, the one an [SCM][net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled] reads
 * to compute change logs.
 *
 * Whatever depends on the commit of a build (like its assisted change) listens to the changes of these
 * properties, the commit of a build being usually set after its creation.
 */
interface SCMBuildCommitPropertyType
