package dev.rahier.unrolled

import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsParityTest {
    private val expectedFolders = listOf(
        "values-ar", "values-bn", "values-de", "values-es", "values-fr", "values-hi", "values-in", "values-it",
        "values-ja", "values-ko", "values-mr", "values-nl", "values-pt", "values-ru", "values-ta", "values-te",
        "values-tr", "values-ur", "values-vi", "values-zh-rCN", "values-zh-rTW"
    )
    private val placeholder = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[sdf]")

    private data class Resources(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun load(folder: String): Resources {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue(file.exists(), "$folder/strings.xml is missing")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val name = node.attributes.getNamedItem("name").nodeValue
            when (node.nodeName) {
                "string" -> if (node.attributes.getNamedItem("translatable")?.nodeValue != "false") {
                    strings[name] = node.textContent
                }
                "plurals" -> {
                    val items = mutableMapOf<String, String>()
                    val children = node.childNodes
                    for (j in 0 until children.length) {
                        val item = children.item(j)
                        if (item.nodeName == "item") items[item.attributes.getNamedItem("quantity").nodeValue] = item.textContent
                    }
                    plurals[name] = items
                }
            }
        }
        return Resources(strings, plurals)
    }

    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text).map { it.value.replace(Regex("^%\\d+\\$"), "%") }.sorted().toList()

    @Test
    fun `every locale has the same keys as the default resources`() {
        val base = load("values")
        for (folder in expectedFolders) {
            val locale = load(folder)
            assertEquals(base.strings.keys, locale.strings.keys, "$folder string keys")
            assertEquals(base.plurals.keys, locale.plurals.keys, "$folder plural keys")
        }
    }

    @Test
    fun `translations keep placeholders and never use an em dash`() {
        val base = load("values")
        for (folder in expectedFolders) {
            val locale = load(folder)
            for ((key, value) in locale.strings) {
                assertFalse(value.isBlank(), "$folder $key is blank")
                assertFalse(value.contains('\u2014'), "$folder $key uses an em dash")
                assertEquals(placeholders(base.strings.getValue(key)), placeholders(value), "$folder $key placeholders")
            }
            for ((key, items) in locale.plurals) {
                assertTrue(items.containsKey("other"), "$folder $key needs an other quantity")
                for ((quantity, value) in items) {
                    assertFalse(value.contains('\u2014'), "$folder $key[$quantity] uses an em dash")
                    assertEquals(placeholders(base.plurals.getValue(key).getValue("other")), placeholders(value), "$folder $key[$quantity] placeholders")
                }
            }
        }
    }
}
