package com.reminderlists.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Bulk item input (TZ 3.3): separators, list markers, the "2/l" amount tail and de-duplication.
class TextFormatBulkTest {

    private fun parse(raw: String) = TextFormat.parseBulkItems(raw)

    @Test
    fun `commas semicolons and line breaks separate alike`() {
        val items = parse("bread, milk; cheese\nbutter")
        assertEquals(listOf("bread", "milk", "cheese", "butter"), items.map { it.text })
        assertTrue(items.all { it.quantity == null && it.unit == null })
    }

    @Test
    fun `amount tail splits into quantity and unit`() {
        val items = parse("milk 2/l, cheese /kg, water 5/, eggs 10/pcs")
        assertEquals(listOf("milk", "cheese", "water", "eggs"), items.map { it.text })
        assertEquals(listOf("2", null, "5", "10"), items.map { it.quantity })
        assertEquals(listOf("l", "kg", null, "pcs"), items.map { it.unit })
    }

    @Test
    fun `a non-numeric tail stays part of the text`() {
        val items = parse("flour t/s, omega 3, tea a/b/c")
        assertEquals(listOf("flour t/s", "omega 3", "tea a/b/c"), items.map { it.text })
        assertTrue(items.all { it.quantity == null && it.unit == null })
    }

    @Test
    fun `an amount with no text before it stays text`() {
        assertEquals(listOf("2/kg"), parse("2/kg").map { it.text })
    }

    @Test
    fun `shared list text pastes back without markers or header`() {
        val items = parse(">>> Groceries\n- bread\nv milk 2/l\n* cheese\n3. wine\n[x] salt\n✅ pepper")
        assertEquals(
            listOf("bread", "milk", "cheese", "wine", "salt", "pepper"),
            items.map { it.text },
        )
        assertEquals("2", items[1].quantity)
        assertEquals("l", items[1].unit)
    }

    @Test
    fun `repeats collapse by text whatever the case or amount`() {
        val items = parse("bread, BREAD, Bread 2/pcs,  bread ")
        assertEquals(listOf("bread"), items.map { it.text })
        assertEquals(null, items.single().quantity)
    }

    @Test
    fun `empty pieces and whitespace produce nothing`() {
        assertEquals(emptyList<ParsedItem>(), parse("  ,;  ,\n \n"))
        assertEquals(emptyList<ParsedItem>(), parse(""))
    }

    @Test
    fun `oversized text and amounts are cut or left as text`() {
        val long = "x".repeat(Limits.ITEM_TEXT + 50)
        assertEquals(Limits.ITEM_TEXT, parse(long).single().text.length)
        // A unit longer than the field allows is not an amount at all — the piece stays text.
        val longUnit = "u".repeat(Limits.UNIT + 1)
        val item = parse("milk 2/$longUnit").single()
        assertEquals("milk 2/$longUnit", item.text)
        assertEquals(null, item.unit)
    }

    @Test
    fun `bulk key matches the dictionary form used against the list`() {
        assertEquals(TextFormat.toDictionaryForm("  МОЛОКО  топлёное "), TextFormat.bulkKey("молоко топлёное"))
    }
}
