package net.nemerosa.ontrack.extension.api.model

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IssueChangeLogExportRequestTest {

    @Test
    fun empty() {
        val request = IssueChangeLogExportRequest()
        val spec = request.groupingSpecification
        assertTrue(spec.isEmpty())
    }

    @Test
    fun one_group() {
        val request = IssueChangeLogExportRequest()
        request.grouping = "Bugs=bug"
        val spec = request.groupingSpecification
        assertEquals(1, spec.size.toLong())
        assertEquals(setOf("bug"), spec["Bugs"])
    }

    @Test
    fun one_group_with_several_values() {
        val request = IssueChangeLogExportRequest()
        request.grouping = "Features=feature,enhancement"
        val spec = request.groupingSpecification
        assertEquals(1, spec.size.toLong())
        assertEquals(setOf("feature", "enhancement"), spec["Features"])
    }

    @Test
    fun two_groups() {
        val request = IssueChangeLogExportRequest()
        request.grouping = "Bugs=bug|Features=feature,enhancement"
        val spec = request.groupingSpecification
        assertEquals(2, spec.size.toLong())
        assertEquals(setOf("bug"), spec["Bugs"])
        assertEquals(setOf("feature", "enhancement"), spec["Features"])
    }

    @Test
    fun group_format_exception_equal_sign() {
        assertFailsWith<ExportRequestGroupingFormatException> {
            val request = IssueChangeLogExportRequest()
            request.grouping = "Bugs=bug=test"
            request.groupingSpecification
        }
    }

    @Test
    fun getExcludedTypes_empty() {
        val request = IssueChangeLogExportRequest()
        request.exclude = ""
        assertTrue(request.excludedTypes.isEmpty())
    }

    @Test
    fun getExcludedTypes_blank() {
        val request = IssueChangeLogExportRequest()
        request.exclude = "  "
        assertTrue(request.excludedTypes.isEmpty())
    }

    @Test
    fun getExcludedTypes_one() {
        val request = IssueChangeLogExportRequest()
        request.exclude = "type1"
        assertEquals(
                setOf("type1"),
                request.excludedTypes)
    }

    @Test
    fun getExcludedTypes_one_trimmed() {
        val request = IssueChangeLogExportRequest()
        request.exclude = " type1  "
        assertEquals(
                setOf("type1"),
                request.excludedTypes)
    }

    @Test
    fun getExcludedTypes_several() {
        val request = IssueChangeLogExportRequest()
        request.exclude = "type1,type2,type3"
        assertEquals(
                setOf("type1", "type2", "type3"),
                request.excludedTypes)
    }

    @Test
    fun getExcludedTypes_several_trimmed() {
        val request = IssueChangeLogExportRequest()
        request.exclude = " type1  ,  type2 ,    type3   "
        assertEquals(
                setOf("type1", "type2", "type3"),
                request.excludedTypes)
    }

}
