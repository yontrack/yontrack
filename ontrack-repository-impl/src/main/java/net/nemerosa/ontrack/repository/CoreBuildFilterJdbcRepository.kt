package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.asOptional
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.createSQL
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.isNotBlank
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository
import java.sql.SQLException
import java.util.*
import javax.sql.DataSource
import kotlin.math.max
import kotlin.math.min

@Repository
class CoreBuildFilterJdbcRepository(
    dataSource: DataSource,
    private val structureRepository: StructureRepository,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : AbstractJdbcRepository(dataSource), CoreBuildFilterRepository {

    override fun projectSearch(
        project: Project,
        form: BuildSearchForm,
        helper: CoreBuildFilterRepositoryHelper,
    ): List<Build> {
        // Query root
        val tables = mutableListOf(
            """
                SELECT DISTINCT(B.ID) FROM BUILDS B
                INNER JOIN BRANCHES BB ON BB.ID = B.BRANCHID
            """
        )
        // Criterias
        val criteria = mutableListOf(
            "BB.PROJECTID = :project"
        )
        // Parameters
        val params = mutableMapOf<String, Any?>()
        params["project"] = project.id()

        // branchName
        val branchName = form.branchName
        if (!branchName.isNullOrBlank()) {
            try {
                branchName.toRegex()
            } catch (_: Exception) {
                // We ignore invalid regexes
                return emptyList()
            }
            criteria += "BB.NAME ~ :branchName"
            params["branchName"] = branchName
        }

        // buildName
        val buildName = form.buildName
        if (!buildName.isNullOrBlank()) {
            if (form.buildExactMatch) {
                criteria += "B.NAME = :buildName"
            } else {
                try {
                    buildName.toRegex()
                } catch (_: Exception) {
                    // We ignore invalid regexes
                    return emptyList()
                }
                criteria += "B.NAME ~ :buildName"
            }
            params["buildName"] = buildName
        }

        // promotionName
        if (!form.promotionName.isNullOrBlank()) {
            tables += """ 
                INNER JOIN PROMOTION_RUNS PR ON PR.BUILDID = B.ID
                INNER JOIN PROMOTION_LEVELS PL ON PL.ID = PR.PROMOTIONLEVELID
            """
            criteria += "PL.NAME = :promotionName"
            params["promotionName"] = form.promotionName
        }

        // validationStampName
        if (!form.validationStampName.isNullOrBlank()) {
            tables += """ 
                INNER JOIN VALIDATION_RUNS VR ON VR.BUILDID = B.ID
                INNER JOIN VALIDATION_STAMPS VS ON VS.ID = VR.VALIDATIONSTAMPID
            """
            criteria += "VS.NAME = :validationStampName"
            params["validationStampName"] = form.validationStampName
        }

        // property
        val property = form.property
        if (!property.isNullOrBlank()) {
            tables += """
                INNER JOIN PROPERTIES PP ON PP.BUILD = B.ID 
            """
            criteria += "PP.TYPE = :property"
            params["property"] = property
            val formPropertyValue = form.propertyValue
            if (!formPropertyValue.isNullOrBlank()) {
                // Gets the property type
                val propertyType = helper.propertyTypeAccessor(property)
                // Gets the search arguments
                val searchArguments = propertyType.getSearchArguments(formPropertyValue)
                // If defined, use them
                if (searchArguments != null && searchArguments.isDefined) {
                    PropertyJdbcRepository.prepareQueryForPropertyValue(
                        searchArguments,
                        tables,
                        criteria,
                        params
                    )
                } else {
                    // No match possible - not performing the request
                    return emptyList()
                }
            }
        }
        val linkedFrom: String? = form.linkedFrom
        if (!linkedFrom.isNullOrBlank()) {
            tables += """ 
                LEFT JOIN BUILD_LINKS BLFROM ON BLFROM.TARGETBUILDID = B.ID
                LEFT JOIN BUILDS BDFROM ON BDFROM.ID = BLFROM.BUILDID 
                LEFT JOIN BRANCHES BRFROM ON BRFROM.ID = BDFROM.BRANCHID 
                LEFT JOIN PROJECTS PJFROM ON PJFROM.ID = BRFROM.PROJECTID
            """
            val projectFrom = linkedFrom.substringBefore(":")
            criteria += "PJFROM.NAME = :fromProject"
            params["fromProject"] = projectFrom
            val buildPattern = linkedFrom.substringAfter(":", "")
            if (buildPattern.isNotBlank()) {
                if ("*" in buildPattern) {
                    criteria += "BDFROM.NAME LIKE :buildFrom"
                    params["buildFrom"] = buildPattern.replace("*", "%")
                } else {
                    criteria += "BDFROM.NAME = :buildFrom"
                    params["buildFrom"] = buildPattern
                }
            }
        }

        // linkedTo
        val linkedTo: String? = form.linkedTo
        if (!linkedTo.isNullOrBlank()) {
            tables +=
                """ 
                    LEFT JOIN BUILD_LINKS BLTO ON BLTO.BUILDID = B.ID
                    LEFT JOIN BUILDS BDTO ON BDTO.ID = BLTO.TARGETBUILDID
                    LEFT JOIN BRANCHES BRTO ON BRTO.ID = BDTO.BRANCHID
                    LEFT JOIN PROJECTS PJTO ON PJTO.ID = BRTO.PROJECTID
                """
            val projectTo = linkedTo.substringBefore(":")
            criteria += "PJTO.NAME = :toProject"
            params["toProject"] = projectTo
            val buildPattern = linkedTo.substringAfter(":", "")
            if (buildPattern.isNotBlank()) {
                if ("*" in buildPattern) {
                    criteria += "BDTO.NAME LIKE :buildTo"
                    params["buildTo"] = buildPattern.replace("*", "%")
                } else {
                    criteria += "BDTO.NAME = :buildTo"
                    params["buildTo"] = buildPattern
                }
            }
        }

        // Extensions
        form.extensions?.forEach { (extension, value) ->
            helper.contribute(
                extension = extension,
                value = value,
                tables = tables,
                criteria = criteria,
                params = params,
            )
        }

        // Final SQL
        val count = min(form.maximumCount, ontrackConfigProperties.buildFilterCountMax)
        val sql = createSQL(tables, criteria) + " ORDER BY B.ID DESC LIMIT $count"

        // Running the query
        return loadBuilds(sql, params)
    }

    /**
     * The fat join query (dreaming of Neo4J) defines the following columns:
     *
     * ```
     * B (BUILDS)
     * PR (PROMOTION_RUNS)
     * PL (PROMOTION_LEVELS)
     * S (last validation run status)
     * VALIDATIONSTAMPID
     * VALIDATIONRUNSTATUSID
     * PP (PROPERTIES)
     * BDFROM (builds linked from)
     * BRFROM (branches linked from)
     * PJFROM (projects linked from)
     * PLFROM (promotions linked from)
     * BDTO (builds linked to)
     * BRTO (branches linked to)
     * PJTO (projects linked to)
     * PLTO (promotions linked to)
     * ```
     */
    override fun standardFilter(
        branch: Branch,
        data: StandardBuildFilterData,
        displayNameProperty: BuildDisplayNameProperty?,
        propertyTypeAccessor: (String) -> PropertyType<*>,
    ): List<Build> {
        val count = min(data.count, ontrackConfigProperties.buildFilterCountMax)
        val params = mutableMapOf<String, Any?>()

        val base = buildStandardFilterQuery(branch, data, params, displayNameProperty, propertyTypeAccessor)
        val query = appendOrderOffsetAndCount(base, 0, count, params)

        val sql = "SELECT DISTINCT(B.ID) $query"

        return catchingInvalidRegex {
            loadBuilds(sql, params)
        }
    }

    override fun standardFilterPagination(
        branch: Branch,
        data: StandardBuildFilterData,
        offset: Int,
        size: Int,
        displayNameProperty: BuildDisplayNameProperty?,
        propertyTypeAccessor: (String) -> PropertyType<*>,
    ): PaginatedList<Build> {
        val count = minOf(size, ontrackConfigProperties.buildFilterCountMax)
        val params = mutableMapOf<String, Any?>()

        val base = buildStandardFilterQuery(branch, data, params, displayNameProperty, propertyTypeAccessor)

        // Count
        @Suppress("SqlSourceToSinkFlow")
        val total = catchingInvalidRegex {
            namedParameterJdbcTemplate!!.queryForObject(
                "SELECT COUNT(B.ID) $base",
                params,
                Int::class.java
            ) ?: 0
        }

        // List
        val sql = appendOrderOffsetAndCount(base, offset, count, params)
        val builds = catchingInvalidRegex {
            loadBuilds("SELECT DISTINCT(B.ID) $sql", params)
        }

        // OK
        return PaginatedList.Companion.create(
            builds,
            offset,
            size,
            total,
        )
    }

    /**
     * The fat join query defines the following columns:
     *
     * ```
     * B (BUILDS)
     * PR (PROMOTION_RUNS)
     * PL (PROMOTION_LEVELS)
     * S (last validation run status)
     * VALIDATIONSTAMPID
     * VALIDATIONRUNSTATUSID
     * PP (PROPERTIES)
     * BDFROM (builds linked from)
     * BRFROM (branches linked from)
     * PJFROM (projects linked from)
     * PLFROM (promotions linked from)
     * BDTO (builds linked to)
     * BRTO (branches linked to)
     * PJTO (projects linked to)
     * PLTO (promotions linked to)
     * ```
     */
    private fun buildStandardFilterQuery(
        branch: Branch,
        data: StandardBuildFilterData,
        params: MutableMap<String, Any?>,
        displayNameProperty: BuildDisplayNameProperty?,
        propertyTypeAccessor: (String) -> PropertyType<*>,
    ): String {
        // Query root
        val tables = mutableListOf("FROM BUILDS B ")
        // Criterias
        val criteria = mutableListOf("B.BRANCHID = :branch")

        // Parameters
        params["branch"] = branch.id()
        var sinceBuildId: Int? = null

        // sincePromotionLevel
        val sincePromotionLevel = data.sincePromotionLevel
        if (isNotBlank(sincePromotionLevel)) {
            // Gets the promotion level ID
            val promotionLevelId = structureRepository
                .getPromotionLevelByName(branch, sincePromotionLevel)
                .map { it.id() }
                .orElse(-1)
            // Gets the last build having this promotion level
            val id = findLastBuildWithPromotionLevel(promotionLevelId)
            if (id != null) {
                sinceBuildId = id
            }
        }

        // withPromotionLevel
        val withPromotionLevel = data.withPromotionLevel
        if (isNotBlank(withPromotionLevel)) {
            tables.add(
                " LEFT JOIN PROMOTION_RUNS PR ON PR.BUILDID = B.ID" + " LEFT JOIN PROMOTION_LEVELS PL ON PL.ID = PR.PROMOTIONLEVELID"
            )
            criteria.add("PL.NAME = :withPromotionLevel")
            params["withPromotionLevel"] = withPromotionLevel
        }

        // afterDate
        val afterDate = data.afterDate
        if (afterDate != null) {
            criteria.add("B.CREATION >= :afterDate")
            params["afterDate"] = dateTimeForDB(afterDate.atTime(0, 0))
        }

        // beforeDate
        val beforeDate = data.beforeDate
        if (beforeDate != null) {
            criteria.add("B.CREATION <= :beforeDate")
            params["beforeDate"] = dateTimeForDB(beforeDate.atTime(23, 59, 59))
        }

        // sinceValidationStamp
        val sinceValidationStamp = data.sinceValidationStamp
        if (!sinceValidationStamp.isNullOrBlank()) {
            // Gets the validation stamp ID
            val validationStampId = getValidationStampId(branch, sinceValidationStamp)
                ?: throw CoreBuildFilterInvalidException(
                    "Could not find validation stamp $sinceValidationStamp."
                )
            // Gets the last build having this validation stamp and the validation status
            val id = findLastBuildWithValidationStamp(validationStampId, data.sinceValidationStampStatus)
            if (id != null) {
                sinceBuildId = if (sinceBuildId == null) {
                    id
                } else {
                    max(sinceBuildId, id)
                }
            }
        }

        // withValidationStamp
        val withValidationStamp = data.withValidationStamp
        if (!withValidationStamp.isNullOrBlank()) {
            tables.add(
                "  LEFT JOIN (" +
                        " SELECT R.BUILDID,  R.VALIDATIONSTAMPID, VRS.VALIDATIONRUNSTATUSID " +
                        " FROM VALIDATION_RUNS R" +
                        " INNER JOIN VALIDATION_RUN_STATUSES VRS ON VRS.ID = (SELECT ID FROM VALIDATION_RUN_STATUSES WHERE VALIDATIONRUNID = R.ID ORDER BY ID DESC LIMIT 1)" +
                        " AND R.ID = (SELECT MAX(ID) FROM VALIDATION_RUNS WHERE BUILDID = R.BUILDID AND VALIDATIONSTAMPID = R.VALIDATIONSTAMPID)" +
                        " ) S ON S.BUILDID = B.ID"
            )
            // Gets the validation stamp ID
            val validationStampId = getValidationStampId(branch, withValidationStamp)
                ?: throw CoreBuildFilterInvalidException(
                    "Could not find validation stamp $withValidationStamp."
                )
            criteria.add("S.VALIDATIONSTAMPID = :validationStampId")
            params["validationStampId"] = validationStampId
            // withValidationStampStatus
            val withValidationStampStatus = data.withValidationStampStatus
            if (isNotBlank(withValidationStampStatus)) {
                criteria.add("S.VALIDATIONRUNSTATUSID = :withValidationStampStatus")
                params["withValidationStampStatus"] = withValidationStampStatus
            }
        }

        // withProperty
        val withProperty = data.withProperty
        if (!withProperty.isNullOrBlank()) {
            tables.add(" LEFT JOIN PROPERTIES PP ON PP.BUILD = B.ID")
            criteria.add("PP.TYPE = :withProperty")
            params["withProperty"] = withProperty
            // withPropertyValue
            val withPropertyValue = data.withPropertyValue
            if (!withPropertyValue.isNullOrBlank()) {
                // Gets the property type
                val propertyType = propertyTypeAccessor(withProperty)
                // Gets the search arguments
                val searchArguments = propertyType.getSearchArguments(withPropertyValue)
                // If defined use them
                if (searchArguments != null && searchArguments.isDefined) {
                    PropertyJdbcRepository.prepareQueryForPropertyValue(
                        searchArguments,
                        tables,
                        criteria,
                        params
                    )
                } else {
                    // No match
                    throw CoreBuildFilterInvalidException(
                        "Property value [$withPropertyValue] for property [$withProperty] cannot be converted to search arguments."
                    )
                }
            }
        }

        // sinceProperty
        val sinceProperty = data.sinceProperty
        if (!sinceProperty.isNullOrBlank()) {
            val sincePropertyValue = data.sincePropertyValue
            val id = findLastBuildWithPropertyValue(branch, sinceProperty, sincePropertyValue, propertyTypeAccessor)
            if (id != null) {
                sinceBuildId = if (sinceBuildId == null) {
                    id
                } else {
                    max(sinceBuildId, id)
                }
            }
        }

        // linkedFrom
        val linkedFrom = data.linkedFrom
        if (isNotBlank(linkedFrom)) {
            tables.add(
                " LEFT JOIN BUILD_LINKS BLFROM ON BLFROM.TARGETBUILDID = B.ID" +
                        " LEFT JOIN BUILDS BDFROM ON BDFROM.ID = BLFROM.BUILDID" +
                        " LEFT JOIN BRANCHES BRFROM ON BRFROM.ID = BDFROM.BRANCHID" +
                        " LEFT JOIN PROJECTS PJFROM ON PJFROM.ID = BRFROM.PROJECTID"
            )
            val project = StringUtils.substringBefore(linkedFrom, ":")
            criteria.add("PJFROM.NAME = :fromProject")
            params["fromProject"] = project
            val buildPattern = StringUtils.substringAfter(linkedFrom, ":")
            if (isNotBlank(buildPattern)) {
                if (StringUtils.contains(buildPattern, "*")) {
                    criteria.add("BDFROM.NAME LIKE :buildFrom")
                    params["buildFrom"] = StringUtils.replace(buildPattern, "*", "%")
                } else {
                    criteria.add("BDFROM.NAME = :buildFrom")
                    params["buildFrom"] = buildPattern
                }
            }
            // linkedFromPromotion
            val linkedFromPromotion = data.linkedFromPromotion
            if (isNotBlank(linkedFromPromotion)) {
                tables.add(
                    " LEFT JOIN PROMOTION_RUNS PRFROM ON PRFROM.BUILDID = BDFROM.ID" + " LEFT JOIN PROMOTION_LEVELS PLFROM ON PLFROM.ID = PRFROM.PROMOTIONLEVELID"
                )
                criteria.add("PLFROM.NAME = :linkedFromPromotion")
                params["linkedFromPromotion"] = linkedFromPromotion
            }
        }

        // linkedTo
        val linkedTo = data.linkedTo
        if (isNotBlank(linkedTo)) {
            tables.add(
                " LEFT JOIN BUILD_LINKS BLTO ON BLTO.BUILDID = B.ID" +
                        " LEFT JOIN BUILDS BDTO ON BDTO.ID = BLTO.TARGETBUILDID" +
                        " LEFT JOIN BRANCHES BRTO ON BRTO.ID = BDTO.BRANCHID" +
                        " LEFT JOIN PROJECTS PJTO ON PJTO.ID = BRTO.PROJECTID"
            )
            val project = StringUtils.substringBefore(linkedTo, ":")
            criteria.add("PJTO.NAME = :toProject")
            params["toProject"] = project
            val buildPattern = StringUtils.substringAfter(linkedTo, ":")
            if (isNotBlank(buildPattern)) {
                if (StringUtils.contains(buildPattern, "*")) {
                    criteria.add("BDTO.NAME LIKE :buildTo")
                    params["buildTo"] = StringUtils.replace(buildPattern, "*", "%")
                } else {
                    criteria.add("BDTO.NAME = :buildTo")
                    params["buildTo"] = buildPattern
                }
            }
            // linkedToPromotion
            val linkedToPromotion = data.linkedToPromotion
            if (isNotBlank(linkedToPromotion)) {
                tables.add(
                    " LEFT JOIN PROMOTION_RUNS PRTO ON PRTO.BUILDID = BDTO.ID" + " LEFT JOIN PROMOTION_LEVELS PLTO ON PLTO.ID = PRTO.PROMOTIONLEVELID"
                )
                criteria.add("PLTO.NAME = :linkedToPromotion")
                params["linkedToPromotion"] = linkedToPromotion
            }
        }

        // withDisplayName
        val withDisplayName = data.withDisplayName
        if (!withDisplayName.isNullOrBlank()) {
            try {
                withDisplayName.toRegex()
            } catch (_: Exception) {
                throw CoreBuildFilterInvalidException(
                    """Invalid regular expression for the build display name: "$withDisplayName"."""
                )
            }
            params["withDisplayName"] = withDisplayName
            if (displayNameProperty != null) {
                // The display name is the value of a property, defaulting to the build name
                tables.add(
                    " LEFT JOIN PROPERTIES PDN ON PDN.BUILD = B.ID AND PDN.TYPE = :displayNamePropertyType"
                )
                params["displayNamePropertyType"] = displayNameProperty.propertyTypeName
                params["displayNamePropertyField"] = displayNameProperty.jsonField
                criteria.add(
                    "COALESCE(NULLIF(PDN.JSON->>:displayNamePropertyField, ''), B.NAME) ~* :withDisplayName"
                )
            } else {
                // No property holding a display name: the build name is the display name
                criteria.add("B.NAME ~* :withDisplayName")
            }
        }

        // Since build?
        if (sinceBuildId != null) {
            criteria.add("B.ID >= :sinceBuildId")
            params["sinceBuildId"] = sinceBuildId
        }

        // Final SQL
        return createSQL(tables, criteria)
    }

    /**
     * Runs [block], converting a regular expression rejected by the database into a
     * [CoreBuildFilterInvalidException], which callers translate into an empty list of builds.
     *
     * A pattern is already checked before the query is built, but the JVM and Postgres regular
     * expression dialects differ, so a pattern accepted by the former can still be rejected by
     * the latter.
     */
    private fun <T> catchingInvalidRegex(block: () -> T): T =
        try {
            block()
        } catch (ex: DataAccessException) {
            val sqlState = (ex.mostSpecificCause as? SQLException)?.sqlState
            if (sqlState == PG_INVALID_REGULAR_EXPRESSION) {
                throw CoreBuildFilterInvalidException(
                    "Invalid regular expression in the build filter."
                )
            } else {
                throw ex
            }
        }

    private fun appendOrderOffsetAndCount(
        sql: String,
        offset: Int,
        count: Int,
        params: MutableMap<String, Any?>,
    ): String {
        params["offset"] = offset
        params["count"] = count
        return "$sql ORDER BY B.ID DESC OFFSET :offset LIMIT :count"
    }

    private fun loadBuilds(sql: String, params: Map<String, Any?>): List<Build> {
        return namedParameterJdbcTemplate!!
            .queryForList(
                sql,
                params,
                Int::class.java
            )
            .map { id -> structureRepository.getBuild(ID.of(id)) }
    }

    override fun nameFilter(
        branch: Branch,
        fromBuild: String?,
        toBuild: String?,
        withPromotionLevel: String?,
        count: Int,
    ): List<Build> {
        // Query root
        val sql = StringBuilder(
            "SELECT DISTINCT(B.ID) FROM BUILDS B" +
                    "                LEFT JOIN PROMOTION_RUNS PR ON PR.BUILDID = B.ID" +
                    "                LEFT JOIN PROMOTION_LEVELS PL ON PL.ID = PR.PROMOTIONLEVELID" +
                    // Branch criteria
                    "                WHERE B.BRANCHID = :branch"
        )

        // Parameters
        val params = mutableMapOf<String, Any?>()
        params["branch"] = branch.id()

        // From build
        val fromBuildId = lastBuild(branch, fromBuild, null)
            .map { it.id() }
        if (!fromBuildId.isPresent) {
            return emptyList()
        }
        sql.append(" AND B.ID >= :fromBuildId")
        params["fromBuildId"] = fromBuildId.get()

        // To build
        if (isNotBlank(toBuild)) {
            val toBuildId = lastBuild(branch, toBuild, null).map { it.id() }
            if (toBuildId.isPresent) {
                sql.append(" AND B.ID <= :toBuildId")
                params["toBuildId"] = toBuildId.get()
            }
        }

        // With promotion
        if (isNotBlank(withPromotionLevel)) {
            sql.append(" AND PL.NAME = :withPromotionLevel")
            params["withPromotionLevel"] = withPromotionLevel
        }

        // Ordering
        sql.append(" ORDER BY B.ID DESC")
        // Limit
        sql.append(" LIMIT :count")
        params["count"] = min(count, ontrackConfigProperties.buildFilterCountMax)

        // Running the query
        return loadBuilds(sql.toString(), params)
    }

    override fun lastBuild(branch: Branch, sinceBuild: String?, withPromotionLevel: String?): Optional<Build> {
        // Query root
        val sql = StringBuilder(
            "SELECT DISTINCT(B.ID) FROM BUILDS B" +
                    "                LEFT JOIN PROMOTION_RUNS PR ON PR.BUILDID = B.ID" +
                    "                LEFT JOIN PROMOTION_LEVELS PL ON PL.ID = PR.PROMOTIONLEVELID" +
                    // Branch criteria
                    "                WHERE B.BRANCHID = :branch"
        )

        // Parameters
        val params = mutableMapOf<String, Any?>()
        params["branch"] = branch.id()

        // Since build
        if (StringUtils.contains(sinceBuild, "*")) {
            sql.append(" AND B.NAME LIKE :buildName")
            params["buildName"] = StringUtils.replace(sinceBuild, "*", "%")
        } else {
            sql.append(" AND B.NAME = :buildName")
            params["buildName"] = sinceBuild
        }

        // With promotion
        if (isNotBlank(withPromotionLevel)) {
            sql.append(" AND PL.NAME = :withPromotionLevel")
            params["withPromotionLevel"] = withPromotionLevel
        }

        // Ordering
        sql.append(" ORDER BY B.ID DESC")
        // Limit
        sql.append(" LIMIT 1")

        // Running the query
        return loadBuilds(sql.toString(), params)
            .firstOrNull()
            .asOptional()
    }

    override fun between(branch: Branch, from: String?, to: String?): List<Build> {
        val sql = StringBuilder(
            "SELECT ID FROM BUILDS WHERE " + "BRANCHID = :branchId "
        )

        val params = mutableMapOf<String, Any?>()
        params["branchId"] = branch.id()
        var fromId: Int?
        var toId: Int? = null

        try {

            // From build
            fromId = structureRepository.getBuildByName(branch.project.name, branch.name, from)
                .orElseThrow { BuildNotFoundException(branch.project.name, branch.name, from) }
                .id()

            // To build
            if (isNotBlank(to)) {
                toId = structureRepository.getBuildByName(branch.project.name, branch.name, to)
                    .orElseThrow { BuildNotFoundException(branch.project.name, branch.name, to) }
                    .id()
            }

        } catch (_: BuildNotFoundException) {
            // If one of the boundaries is not found, returns an empty list
            return emptyList()
        }

        if (toId != null) {
            if (toId < fromId) {
                val i = toId
                toId = fromId
                fromId = i
            }
        }

        sql.append(" AND ID >= :fromId")
        params["fromId"] = fromId
        if (toId != null) {
            sql.append(" AND ID <= :toId")
            params["toId"] = toId
        }

        // Ordering
        sql.append(" ORDER BY ID DESC")

        // Query
        return loadBuilds(sql.toString(), params)
    }

    private fun findLastBuildWithPropertyValue(
        branch: Branch,
        propertyTypeName: String,
        propertyValue: String?,
        propertyTypeAccessor: (String) -> PropertyType<*>,
    ): Int? {
        // SQL
        val tables = mutableListOf(
            "SELECT B.ID " +
                    "FROM BUILDS B " +
                    "LEFT JOIN PROPERTIES PP ON PP.BUILD = B.ID "
        )
        val criteria = mutableListOf(
            "B.BRANCHID = :branchId", "PP.TYPE = :propertyType "
        )

        val params = mutableMapOf<String, Any?>()
        params["branchId"] = branch.id()
        params["propertyType"] = propertyTypeName

        if (!propertyValue.isNullOrBlank()) {
            // Gets the property type
            val propertyType = propertyTypeAccessor(propertyTypeName)
            // Gets the search arguments for a property value
            val searchArguments = propertyType.getSearchArguments(propertyValue)
            // If defined use them
            if (searchArguments != null && searchArguments.isDefined) {
                PropertyJdbcRepository.prepareQueryForPropertyValue(
                    searchArguments,
                    tables,
                    criteria,
                    params
                )
            } else {
                // No match
                return null
            }
        }

        // Build ID
        val sql = createSQL(tables, criteria) +
                " ORDER BY B.ID DESC LIMIT 1"

        // Runs the query
        return getFirstItem(
            sql,
            params,
            Int::class.java
        )
    }

    private fun getValidationStampId(branch: Branch, validationStampName: String): Int? {
        return structureRepository
            .getValidationStampByName(branch, validationStampName)
            .orElse(null)
            ?.id()
    }

    private fun findLastBuildWithValidationStamp(validationStampId: Int, status: String?): Int? {
        val sql = StringBuilder(
            "SELECT VR.BUILDID FROM VALIDATION_RUN_STATUSES VRS\n" +
                    "INNER JOIN VALIDATION_RUNS VR ON VR.ID = VRS.VALIDATIONRUNID\n" +
                    "WHERE VR.VALIDATIONSTAMPID = :validationStampId\n"
        )
        // Parameters
        val params = params("validationStampId", validationStampId)
        // Status criteria
        if (isNotBlank(status)) {
            sql.append("AND VRS.VALIDATIONRUNSTATUSID = :status\n")
            params.addValue("status", status)
        }
        // Order & limit
        sql.append("ORDER BY VR.BUILDID DESC LIMIT 1\n")
        // Build ID
        return getFirstItem(
            sql.toString(),
            params,
            Int::class.java
        )
    }

    private fun findLastBuildWithPromotionLevel(promotionLevelId: Int): Int? {
        return getFirstItem(
            "SELECT BUILDID FROM PROMOTION_RUNS WHERE PROMOTIONLEVELID = :promotionLevelId ORDER BY BUILDID DESC LIMIT 1",
            params("promotionLevelId", promotionLevelId),
            Int::class.java
        )
    }

    companion object {
        /**
         * Postgres SQL state for an invalid regular expression.
         */
        private const val PG_INVALID_REGULAR_EXPRESSION = "2201B"
    }
}
