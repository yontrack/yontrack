package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.labels.LabelCategoryNameAlreadyExistException
import net.nemerosa.ontrack.model.labels.LabelForm
import net.nemerosa.ontrack.model.labels.LabelIdNotFoundException
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class LabelJdbcRepository(
        dataSource: DataSource
) : AbstractJdbcRepository(dataSource), LabelRepository {

    override fun findLabels(category: String?, name: String?): List<LabelRecord> {
        val sql: String
        val params: MapSqlParameterSource
        if (category == null) {
            if (name == null) {
                sql = "SELECT * FROM LABEL ORDER BY CATEGORY, NAME"
                params = MapSqlParameterSource()
            } else {
                sql = "SELECT * FROM LABEL WHERE NAME = :name ORDER BY CATEGORY, NAME"
                params = params("name", name)
            }
        } else if (name == null) {
            sql = "SELECT * FROM LABEL WHERE CATEGORY = :category ORDER BY CATEGORY, NAME"
            params = params("category", category)
        } else {
            sql = "SELECT * FROM LABEL WHERE CATEGORY = :category AND NAME = :name ORDER BY CATEGORY, NAME"
            params = params("category", category).addValue("name", name)
        }
        return namedParameterJdbcTemplate!!.query(
                sql,
                params
        ) { rs, _ -> rsConversion(rs) }
    }

    override fun newLabel(form: LabelForm): LabelRecord {
        try {
            val id = dbCreate("""
                        INSERT INTO LABEL(category, name, description, color)
                        VALUES (:category, :name, :description, :color)
                    """,
                    params("category", form.category)
                            .addValue("name", form.name)
                            .addValue("description", form.description)
                            .addValue("color", form.color)
            )
            return LabelRecord(
                    id = id,
                    category = form.category,
                    name = form.name,
                    description = form.description,
                    color = form.color,
            )
        } catch (_: DuplicateKeyException) {
            throw LabelCategoryNameAlreadyExistException(form.category, form.name)
        }
    }

    override fun updateLabel(labelId: Int, form: LabelForm): LabelRecord {
        try {
            namedParameterJdbcTemplate!!.update("""
                        UPDATE LABEL
                        SET category = :category,
                            name = :name,
                            description = :description,
                            color = :color
                        WHERE id = :id
                    """,
                    params("category", form.category)
                            .addValue("name", form.name)
                            .addValue("description", form.description)
                            .addValue("color", form.color)
                            .addValue("id", labelId)
            )
            return getLabel(labelId)
        } catch (_: DuplicateKeyException) {
            throw LabelCategoryNameAlreadyExistException(form.category, form.name)
        }
    }

    override fun deleteLabel(labelId: Int): Ack {
        return Ack.one(
                namedParameterJdbcTemplate!!.update(
                        "DELETE FROM LABEL WHERE ID = :id",
                        params("id", labelId)
                )
        )
    }

    override fun findLabelById(labelId: Int): LabelRecord? {
        return getFirstItem(
                "SELECT * FROM LABEL WHERE ID = :id",
                params("id", labelId)
        ) { rs, _ -> rsConversion(rs) }
    }

    override fun getLabel(labelId: Int): LabelRecord {
        return findLabelById(labelId) ?: throw LabelIdNotFoundException(labelId)
    }

    override val labels: List<LabelRecord>
        get() = jdbcTemplate!!.query(
                "SELECT * FROM LABEL ORDER BY CATEGORY, NAME"
        ) { rs, _ -> rsConversion(rs) }

    private val rsConversion: (ResultSet) -> LabelRecord = { rs: ResultSet -> rs.toLabelRecord() }
}

/**
 * Reads a [LabelRecord] from the current row of a result set over the `LABEL` table columns.
 */
internal fun ResultSet.toLabelRecord() = LabelRecord(
        id = getInt("ID"),
        category = getString("CATEGORY"),
        name = getString("NAME"),
        description = getString("DESCRIPTION"),
        color = getString("COLOR"),
)