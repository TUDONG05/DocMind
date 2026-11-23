package com.example.docmind.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExcelSheetData(
    val sheetName: String,
    val rows: List<List<String>>
)

object ExcelParserUtils {

    suspend fun parseXlsx(context: Context, filePathOrUri: String): Result<List<ExcelSheetData>> = withContext(Dispatchers.IO) {
        try {
            Log.d("DocMindDebug", "ExcelParserUtils - parseXlsx started for path: $filePathOrUri")
            val inputStream: InputStream? = if (filePathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(filePathOrUri))
            } else {
                val file = File(filePathOrUri)
                if (file.exists()) file.inputStream() else null
            }

            if (inputStream == null) {
                Log.e("DocMindDebug", "ExcelParserUtils - inputStream is null for path: $filePathOrUri")
                return@withContext Result.failure(IllegalArgumentException("Không thể mở tập tin Excel (File không tồn tại hoặc không có quyền đọc)."))
            }

            // Read all zip entries into memory map
            val zipEntries = mutableMapOf<String, ByteArray>()
            val zipInputStream = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                zipEntries[entry.name] = zipInputStream.readBytes()
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            Log.d("DocMindDebug", "ExcelParserUtils - Zip entries found: ${zipEntries.keys}")

            // 1. Parse sharedStrings.xml robustly (<si> blocks accumulating all text)
            val sharedStrings = mutableListOf<String>()
            val sharedStringsBytes = zipEntries["xl/sharedStrings.xml"]
            if (sharedStringsBytes != null) {
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
                val parser = factory.newPullParser()
                parser.setInput(sharedStringsBytes.inputStream(), "UTF-8")
                var eventType = parser.eventType
                var inSi = false
                var inT = false
                val currentSiText = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawTag = parser.name ?: ""
                    val tagName = if (rawTag.contains(":")) rawTag.substringAfter(":") else rawTag
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tagName == "si") {
                                inSi = true
                                currentSiText.clear()
                            } else if (tagName == "t" && inSi) {
                                inT = true
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inSi && inT) {
                                currentSiText.append(parser.text ?: "")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tagName == "t" && inSi) {
                                inT = false
                            } else if (tagName == "si") {
                                inSi = false
                                sharedStrings.add(currentSiText.toString())
                            }
                        }
                    }
                    eventType = parser.next()
                }
                Log.d("DocMindDebug", "ExcelParserUtils - Parsed sharedStrings count: ${sharedStrings.size}")
            }

            // 2. Parse workbook.xml to get sheet names and r:id mapping
            val sheetInfoList = mutableListOf<Pair<String, String>>()
            val workbookBytes = zipEntries["xl/workbook.xml"]
            if (workbookBytes != null) {
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
                val parser = factory.newPullParser()
                parser.setInput(workbookBytes.inputStream(), "UTF-8")
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawTag = parser.name ?: ""
                    val tagName = if (rawTag.contains(":")) rawTag.substringAfter(":") else rawTag
                    if (eventType == XmlPullParser.START_TAG && tagName == "sheet") {
                        var name = "Sheet"
                        var rId = ""
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i) ?: ""
                            val localAttrName = if (attrName.contains(":")) attrName.substringAfter(":") else attrName
                            val attrVal = parser.getAttributeValue(i) ?: ""
                            if (localAttrName == "name") name = attrVal
                            if (localAttrName == "id") rId = attrVal
                        }
                        sheetInfoList.add(Pair(name, rId))
                    }
                    eventType = parser.next()
                }
                Log.d("DocMindDebug", "ExcelParserUtils - Parsed sheetInfoList: $sheetInfoList")
            }

            // 3. Parse workbook.xml.rels to map rId -> target worksheet path
            val relsMap = mutableMapOf<String, String>()
            val relsBytes = zipEntries["xl/_rels/workbook.xml.rels"]
            if (relsBytes != null) {
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
                val parser = factory.newPullParser()
                parser.setInput(relsBytes.inputStream(), "UTF-8")
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawTag = parser.name ?: ""
                    val tagName = if (rawTag.contains(":")) rawTag.substringAfter(":") else rawTag
                    if (eventType == XmlPullParser.START_TAG && tagName == "Relationship") {
                        var id = ""
                        var target = ""
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i) ?: ""
                            val attrVal = parser.getAttributeValue(i) ?: ""
                            if (attrName == "Id") id = attrVal
                            if (attrName == "Target") target = attrVal
                        }
                        if (id.isNotEmpty() && target.isNotEmpty()) {
                            val cleanTarget = target.removePrefix("/").removePrefix("./")
                            val fullTarget = when {
                                cleanTarget.startsWith("xl/") -> cleanTarget
                                cleanTarget.startsWith("worksheets/") -> "xl/$cleanTarget"
                                cleanTarget.startsWith("../") -> cleanTarget.replace("../", "xl/")
                                else -> "xl/worksheets/$cleanTarget"
                            }
                            relsMap[id] = fullTarget
                        }
                    }
                    eventType = parser.next()
                }
                Log.d("DocMindDebug", "ExcelParserUtils - Parsed relsMap: $relsMap")
            }

            val excelSheets = mutableListOf<ExcelSheetData>()
            if (sheetInfoList.isEmpty()) {
                val sheetEntries = zipEntries.keys.filter { it.contains("worksheets/sheet") }.sorted()
                sheetEntries.forEachIndexed { index, path ->
                    val sheetData = parseWorksheet(zipEntries[path], sharedStrings, "Sheet ${index + 1}")
                    excelSheets.add(sheetData)
                }
            } else {
                sheetInfoList.forEachIndexed { index, pair ->
                    val sheetName = pair.first
                    val rId = pair.second
                    var targetPath = relsMap[rId]
                    if (targetPath == null || !zipEntries.containsKey(targetPath)) {
                        targetPath = "xl/worksheets/sheet${index + 1}.xml"
                    }
                    val sheetBytes = zipEntries[targetPath]
                    val sheetData = parseWorksheet(sheetBytes, sharedStrings, sheetName)
                    excelSheets.add(sheetData)
                }
            }

            if (excelSheets.isEmpty()) {
                excelSheets.add(ExcelSheetData("Sheet1", listOf(listOf("Không tìm thấy dữ liệu bảng tính trong file Excel.")) ))
            }

            Log.d("DocMindDebug", "ExcelParserUtils - Parsed total sheets: ${excelSheets.size}")
            Result.success(excelSheets)
        } catch (e: Exception) {
            Log.e("DocMindDebug", "ExcelParserUtils - Exception in parseXlsx: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private fun parseWorksheet(xmlBytes: ByteArray?, sharedStrings: List<String>, sheetName: String): ExcelSheetData {
        if (xmlBytes == null) return ExcelSheetData(sheetName, emptyList())

        val rowsMap = mutableMapOf<Int, MutableMap<Int, String>>()
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(xmlBytes.inputStream(), "UTF-8")

            var eventType = parser.eventType
            var currentRowIndex = 0
            var currentCellCol = 0
            var cellType: String? = null
            var inV = false
            var inIs = false
            var inT = false
            val cellValueBuilder = StringBuilder()
            val cellTextBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawTag = parser.name ?: ""
                val tagName = if (rawTag.contains(":")) rawTag.substringAfter(":") else rawTag

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "row" -> {
                                val rAttr = parser.getAttributeValue(null, "r")
                                if (rAttr != null) {
                                    currentRowIndex = rAttr.toIntOrNull()?.minus(1) ?: currentRowIndex
                                }
                            }
                            "c" -> {
                                val rAttr = parser.getAttributeValue(null, "r")
                                if (rAttr != null) {
                                    currentCellCol = columnLettersToIndex(rAttr)
                                }
                                cellType = parser.getAttributeValue(null, "t")
                                cellValueBuilder.clear()
                            }
                            "v" -> {
                                inV = true
                                cellValueBuilder.clear()
                            }
                            "is" -> {
                                inIs = true
                                cellTextBuilder.clear()
                            }
                            "t" -> {
                                if (inIs) {
                                    inT = true
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV) {
                            cellValueBuilder.append(parser.text ?: "")
                        } else if (inIs && inT) {
                            cellTextBuilder.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "v" -> inV = false
                            "t" -> if (inIs) inT = false
                            "is" -> inIs = false
                            "c" -> {
                                val rawVal = if (cellType == "s") {
                                    val sIndex = cellValueBuilder.toString().toIntOrNull()
                                    if (sIndex != null && sIndex in sharedStrings.indices) sharedStrings[sIndex] else cellValueBuilder.toString()
                                } else if (cellType == "inlineStr" || inIs) {
                                    cellTextBuilder.toString()
                                } else {
                                    cellValueBuilder.toString()
                                }

                                if (rawVal.isNotBlank()) {
                                    rowsMap.getOrPut(currentRowIndex) { mutableMapOf() }[currentCellCol] = rawVal
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("DocMindDebug", "ExcelParserUtils - parseWorksheet error: ${e.localizedMessage}", e)
        }

        val sortedRows = rowsMap.toSortedMap()
        val maxCol = sortedRows.values.maxOfOrNull { it.keys.maxOrNull() ?: 0 } ?: 0
        val finalRows = mutableListOf<List<String>>()

        for ((_, colMap) in sortedRows) {
            val rowList = mutableListOf<String>()
            for (col in 0..maxCol) {
                rowList.add(colMap[col] ?: "")
            }
            finalRows.add(rowList)
        }

        if (finalRows.isEmpty()) {
            finalRows.add(listOf("Sheet trống."))
        }

        Log.d("DocMindDebug", "ExcelParserUtils - Sheet '$sheetName' parsed rows count: ${finalRows.size}")
        return ExcelSheetData(sheetName, finalRows)
    }

    private fun columnLettersToIndex(cellRef: String): Int {
        var col = 0
        for (char in cellRef) {
            if (char in 'A'..'Z') {
                col = col * 26 + (char - 'A' + 1)
            } else if (char in 'a'..'z') {
                col = col * 26 + (char - 'a' + 1)
            }
        }
        return (col - 1).coerceAtLeast(0)
    }
}
