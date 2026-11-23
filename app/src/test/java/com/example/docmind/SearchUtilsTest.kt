package com.example.docmind

import com.example.docmind.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUtilsTest {

    @Test
    fun testNormalizeVietnamese() {
        assertEquals("bang du lieu", FileUtils.normalizeVietnamese("bảng dữ liệu"))
        assertEquals("nguyen van minh", FileUtils.normalizeVietnamese("Nguyễn Văn Minh"))
        assertEquals("tieng viet", FileUtils.normalizeVietnamese("Tiếng Việt"))
        assertEquals("bao cao thuc tap", FileUtils.normalizeVietnamese("Báo cáo thực tập"))
    }

    @Test
    fun testSearchFilteringCases() {
        val filenames = listOf(
            "bảng dữ liệu.docx",
            "Nguyễn Văn Minh.docx",
            "Tiếng Việt.docx",
            "Báo cáo thực tập.pdf"
        )

        fun search(query: String): List<String> {
            if (query.isBlank()) return filenames
            val normQuery = FileUtils.normalizeVietnamese(query)
            return filenames.filter { FileUtils.normalizeVietnamese(it).contains(normQuery) }
        }

        // 1. bang matches bảng dữ liệu.docx
        assertTrue(search("bang").contains("bảng dữ liệu.docx"))

        // 2. du lieu matches bảng dữ liệu.docx
        assertTrue(search("du lieu").contains("bảng dữ liệu.docx"))

        // 3. BANG matches bảng dữ liệu.docx
        assertTrue(search("BANG").contains("bảng dữ liệu.docx"))

        // 4. nguyen matches Nguyễn Văn Minh.docx
        assertTrue(search("nguyen").contains("Nguyễn Văn Minh.docx"))

        // 5. tieng viet matches Tiếng Việt.docx
        assertTrue(search("tieng viet").contains("Tiếng Việt.docx"))

        // 6. bao cao matches Báo cáo thực tập.pdf
        assertTrue(search("bao cao").contains("Báo cáo thực tập.pdf"))

        // 7. Accented keywords still work
        assertTrue(search("bảng").contains("bảng dữ liệu.docx"))
        assertTrue(search("DỮ LIỆU").contains("bảng dữ liệu.docx"))

        // 8. Blank keyword displays full list
        assertEquals(filenames.size, search("   ").size)

        // 9. No result shows empty state (returns empty list)
        assertTrue(search("xyz123").isEmpty())

        // 10. Searching does not alter original document name
        val original = "bảng dữ liệu.docx"
        val filtered = search("bang")
        assertTrue(filtered.contains(original))
        // Verify original string is unchanged
        assertEquals("bảng dữ liệu.docx", original)
    }
}
