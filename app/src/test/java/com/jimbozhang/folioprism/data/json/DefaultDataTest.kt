package com.jimbozhang.folioprism.data.json

import com.jimbozhang.folioprism.domain.assertDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDataTest {

    @Test
    fun defaultCategoriesMatchSpecifiedRule() {
        val categories = DefaultData.defaultCategories()

        assertEquals(7, categories.size)
        val dividend = categories.first()
        assertDecimal("32", dividend.targetPct)
        assertDecimal("28.8", dividend.minPct)
        assertDecimal("35.2", dividend.maxPct)
        assertEquals("dividend", dividend.specialRule)

        categories.drop(1).forEach { category ->
            // 1/6 of the non-dividend base, band at ±10% of the target.
            assertDecimal("16.6666666667", category.targetPct)
            assertDecimal("15", category.minPct)
            assertDecimal("18.3333333333", category.maxPct)
            assertEquals(null, category.specialRule)
        }
    }
}
