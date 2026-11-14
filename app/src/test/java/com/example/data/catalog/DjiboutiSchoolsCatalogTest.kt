package com.example.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiboutiSchoolsCatalogTest {

    @Test
    fun catalogContainsAllOfficialDjiboutiVilleSchoolsWithUniqueIdsAndCodes() {
        val all = DjiboutiSchoolsCatalog.allSchools
        assertEquals(39, all.size)
        assertEquals(18, DjiboutiSchoolsCatalog.primarySchools.size)
        assertEquals(17, DjiboutiSchoolsCatalog.middleSchools.size)
        assertEquals(4, DjiboutiSchoolsCatalog.highSchools.size)

        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(all.size, all.map { it.code }.distinct().size)
        assertTrue(all.all { it.id.matches(Regex("^[a-zA-Z0-9_\\-]+$")) })
        assertTrue(all.all { it.name.length in 2..160 && it.code.length in 1..30 })
    }

    @Test
    fun filterByLevelAndQuery_matchesExpectedSchools() {
        val ledResults = DjiboutiSchoolsCatalog.filter(SchoolLevelFilter.LYCEE, "LED")
        assertEquals(1, ledResults.size)
        assertEquals("Lycée d'État de Djibouti (LED)", ledResults.first().name)

        val hodanPrimary = DjiboutiSchoolsCatalog.filter(SchoolLevelFilter.PRIMAIRE, "Hodan")
        assertEquals(2, hodanPrimary.size)

        val hodanCollege = DjiboutiSchoolsCatalog.filter(SchoolLevelFilter.COLLEGE, "Hodan")
        assertEquals(3, hodanCollege.size)

        val lic = DjiboutiSchoolsCatalog.filter(SchoolLevelFilter.LYCEE, "Omar Kamil Warsama")
        assertEquals(1, lic.size)
    }
}
