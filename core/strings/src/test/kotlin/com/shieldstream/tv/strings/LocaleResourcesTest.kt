package com.shieldstream.tv.strings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocaleResourcesTest {
    private val defaultResources = readResources(File("src/main/res/values"))
    private val spanishResources = readResources(File("src/main/res/values-es"))

    @Test
    fun `English and Spanish declare the same translatable resource keys`() {
        assertEquals(defaultResources.keys, spanishResources.keys)
    }

    @Test
    fun `locale files do not declare duplicate resource keys`() {
        assertNoDuplicates(File("src/main/res/values"))
        assertNoDuplicates(File("src/main/res/values-es"))
    }

    @Test
    fun `translations are not blank`() {
        (defaultResources + spanishResources).forEach { (key, resource) ->
            assertTrue("$key is blank", resource.text.isNotBlank())
            resource.plurals.forEach { (quantity, text) ->
                assertTrue("$key ($quantity) is blank", text.isNotBlank())
            }
        }
    }

    @Test
    fun `translations use the same positional placeholders`() {
        defaultResources.forEach { (key, english) ->
            val spanish = spanishResources.getValue(key)
            assertEquals(key, english.text.placeholders(), spanish.text.placeholders())
            assertEquals(key, english.plurals.keys, spanish.plurals.keys)
            english.plurals.forEach { (quantity, text) ->
                assertEquals(
                    "$key ($quantity)",
                    text.placeholders(),
                    spanish.plurals.getValue(quantity).placeholders(),
                )
            }
        }
    }

    @Test
    fun `formatted resources use positional placeholders`() {
        val nonPositional = Regex("%(?!%)(?:[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z])")
        (defaultResources + spanishResources).forEach { (key, resource) ->
            val values = resource.plurals.values + resource.text
            values.forEach { value ->
                val withoutLiteralPercents = value.replace("%%", "")
                assertFalse("$key has a non-positional placeholder: $value", nonPositional.containsMatchIn(withoutLiteralPercents))
            }
        }
    }

    private fun assertNoDuplicates(directory: File) {
        val keys = resourceFiles(directory).flatMap { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = document.documentElement.childNodes
            buildList {
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.tagName in RESOURCE_TYPES && element.getAttribute("translatable") != "false") {
                        add("${element.tagName}:${element.getAttribute("name")}")
                    }
                }
            }
        }
        assertEquals("Duplicate resource keys in ${directory.path}", keys.distinct().size, keys.size)
    }

    private fun readResources(directory: File): Map<String, LocalizedResource> {
        val entries = resourceFiles(directory).flatMap { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = document.documentElement.childNodes
            buildList {
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.tagName !in RESOURCE_TYPES || element.getAttribute("translatable") == "false") continue
                    val key = "${element.tagName}:${element.getAttribute("name")}"
                    val plurals = if (element.tagName == "plurals") {
                        element.getElementsByTagName("item").let { items ->
                            (0 until items.length).associate { itemIndex ->
                                val item = items.item(itemIndex) as Element
                                item.getAttribute("quantity") to item.textContent
                            }
                        }
                    } else {
                        emptyMap()
                    }
                    add(key to LocalizedResource(element.textContent, plurals))
                }
            }
        }
        val duplicate = entries.groupBy { it.first }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate resource keys in ${directory.path}: $duplicate", duplicate.isEmpty())
        return entries.toMap()
    }

    private fun resourceFiles(directory: File): List<File> =
        directory.listFiles { file -> file.extension == "xml" }?.sortedBy { it.name }.orEmpty()

    private fun String.placeholders(): List<String> = PLACEHOLDER.findAll(this)
        .map { "${it.groupValues[1]}:${it.groupValues[2]}" }
        .sorted()
        .toList()

    private data class LocalizedResource(
        val text: String,
        val plurals: Map<String, String>,
    )

    private companion object {
        val RESOURCE_TYPES = setOf("string", "plurals", "string-array")
        val PLACEHOLDER = Regex("%(\\d+)\\$([a-zA-Z])")
    }
}
