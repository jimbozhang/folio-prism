package com.jimbozhang.folioprism.domain.model

import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.portfolio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteCategoryTest {

    @Test
    fun addsBuiltInSatelliteCategoryOnce() {
        val once = portfolio().withSatelliteCategory()

        assertEquals(8, once.settings.categories.size)
        val satellite = once.settings.categories.last()
        assertEquals(CategoryIds.SATELLITE, satellite.id)
        assertEquals(SATELLITE_RULE, satellite.specialRule)
        assertEquals(CategoryNames.SATELLITE, satellite.name)
        assertTrue(
            satellite.order >
                once.settings.categories.dropLast(1).maxOf { it.order },
        )

        // Calling it again must not duplicate the category.
        assertEquals(8, once.withSatelliteCategory().settings.categories.size)
    }

    @Test
    fun keepsAnExistingSatelliteCategory() {
        val base = portfolio()
        val custom = base.copy(
            settings = base.settings.copy(
                categories = base.settings.categories + Category(
                    id = "my_satellite",
                    name = "任意名字",
                    order = 99,
                    color = "#000000",
                    targetPct = bd("0"),
                    minPct = bd("0"),
                    maxPct = bd("0"),
                    specialRule = SATELLITE_RULE,
                ),
            ),
        )

        val ensured = custom.withSatelliteCategory()

        assertEquals(8, ensured.settings.categories.size)
        assertEquals("my_satellite", ensured.settings.categories.last().id)
    }
}
