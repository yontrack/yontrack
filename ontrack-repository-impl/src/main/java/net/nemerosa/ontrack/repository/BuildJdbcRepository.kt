package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class BuildJdbcRepository(
    dataSource: DataSource,
    private val branchJdbcRepositoryAccessor: BranchJdbcRepositoryAccessor,
) : AbstractJdbcRepository(dataSource), BuildJdbcRepositoryAccessor {

    override fun getBuild(id: ID, branch: Branch?): Build =
        getFirstItem(
            """
               SELECT *
                FROM builds
                WHERE id = :id
            """,
            params("id", id.value)
        ) { rs, _ ->
            Build(
                id = id(rs),
                name = rs.getString("name"),
                description = rs.getString("description"),
                branch = branch ?: branchJdbcRepositoryAccessor.getBranch(id(rs, "branchid")),
                signature = readSignatureWithActor(rs)
            )
        } ?: error("Build not found: $id")

}