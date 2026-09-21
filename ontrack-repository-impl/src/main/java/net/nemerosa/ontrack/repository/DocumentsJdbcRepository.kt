package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.Document
import net.nemerosa.ontrack.common.DocumentInfo
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.support.DocumentsRepository
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import org.springframework.util.unit.DataSize
import javax.sql.DataSource

@Repository
@ConditionalOnProperty(
        name = [OntrackConfigProperties.DOCUMENTS_ENGINE],
        havingValue = OntrackConfigProperties.DocumentProperties.JDBC,
        matchIfMissing = true
)
class DocumentsJdbcRepository(
        dataSource: DataSource,
        private val ontrackConfigProperties: OntrackConfigProperties
) : AbstractJdbcRepository(dataSource), DocumentsRepository {

    override fun storeDocument(store: String, name: String, document: Document) {
        if (document.content.size > ontrackConfigProperties.documents.maxSize.toBytes()) {
            throw DocumentMaxSizeExceededException(
                    maxSize = ontrackConfigProperties.documents.maxSize.toString(),
                    actualSize = DataSize.ofBytes(document.content.size.toLong()).toString()
            )
        }
        deleteDocument(store, name)
        namedParameterJdbcTemplate!!.update(
                "INSERT INTO DOCUMENTS(STORE, NAME, DOCTYPE, DOCBYTES) VALUES (:store, :name, :doctype, :docbytes)",
                params(STORE, store)
                        .addValue(NAME, name)
                        .addValue(DOCTYPE, document.type)
                        .addValue(DOCBYTES, document.content)
        )
    }

    override fun getDocumentStores(): List<String> {
        return jdbcTemplate!!.queryForList(
                "SELECT DISTINCT(STORE) FROM DOCUMENTS",
                String::class.java
        ).filterNotNull().sorted()
    }

    override fun getDocumentNames(store: String): List<String> {
        return namedParameterJdbcTemplate!!.queryForList(
                "SELECT NAME FROM DOCUMENTS WHERE STORE = :store ORDER BY NAME",
                params(STORE, store),
                String::class.java
        ).filterNotNull()
    }

    override fun getSize(store: String): Long {
        return namedParameterJdbcTemplate!!.queryForObject(
                "SELECT SUM(LENGTH(DOCBYTES)) FROM DOCUMENTS WHERE STORE = :store",
                params(STORE, store),
                Long::class.java
        ) ?: 0
    }

    override fun getCount(store: String): Long {
        return namedParameterJdbcTemplate!!.queryForObject(
                "SELECT COUNT(NAME) FROM DOCUMENTS WHERE STORE = :store",
                params(STORE, store),
                Long::class.java
        ) ?: 0
    }

    override fun loadDocument(store: String, name: String): Document? {
        return getFirstItem(
                "SELECT * FROM DOCUMENTS WHERE STORE = :store AND NAME = :name",
                params("store", store).addValue("name", name)
        ) { rs, _ ->
            toDocument(rs, "DOCTYPE", "DOCBYTES")
        }
    }

    override fun getDocumentInfo(store: String, name: String): DocumentInfo? {
        return getFirstItem(
                "SELECT DOCTYPE, LENGTH(DOCBYTES) AS SIZE FROM DOCUMENTS WHERE STORE = :store AND NAME = :name",
                params("store", store).addValue("name", name)
        ) { rs, _ ->
            DocumentInfo(
                    rs.getString("DOCTYPE"),
                    rs.getLong("SIZE")
            )
        }
    }

    override fun documentExists(store: String, name: String): Boolean {
        return getFirstItem(
                "SELECT NAME FROM DOCUMENTS WHERE STORE = :store AND NAME = :name",
                params("store", store).addValue("name", name),
                String::class.java
        ) != null
    }

    override fun deleteDocument(store: String, name: String): Ack {
        val count = namedParameterJdbcTemplate!!.update(
                "DELETE FROM DOCUMENTS WHERE STORE = :store AND NAME = :name",
                params("store", store).addValue("name", name)
        )
        return Ack.validate(count > 0)
    }

    companion object {
        const val STORE = "store"
        const val NAME = "name"
        const val DOCTYPE = "doctype"
        const val DOCBYTES = "docbytes"
    }

}