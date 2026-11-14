package com.example.data.seed

import com.example.data.catalog.DjiboutiSchoolsCatalog
import com.example.data.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiboutiSchoolsSeederTest {

    @Test
    fun buildSchoolDocumentMap_coversAll39DjiboutiSchoolsWithSchemaMetadata() {
        val region = Region(
            id = DjiboutiSchoolsCatalog.REGION_ID,
            name = DjiboutiSchoolsCatalog.REGION_NAME,
            code = DjiboutiSchoolsCatalog.REGION_CODE,
            nationalAdminUid = "admin_1"
        )

        val payloads = DjiboutiSchoolsCatalog.allSchools.map { entry ->
            entry to DjiboutiSchoolsSeeder.buildSchoolDocumentMap(
                entry = entry,
                region = region,
                actorUid = "admin_1",
                nationalAdminUid = "admin_1",
                authorizedUids = listOf("admin_1"),
                useServerTimestamps = false
            )
        }

        assertEquals(39, payloads.size)
        assertEquals(18, payloads.count { it.second["schoolType"] == "primaire" })
        assertEquals(17, payloads.count { it.second["schoolType"] == "college" })
        assertEquals(4, payloads.count { it.second["schoolType"] == "lycee" })

        payloads.forEach { (entry, map) ->
            assertEquals(entry.id, map["tenantId"])
            assertEquals(entry.name, map["name"])
            assertEquals(entry.code, map["code"])
            assertEquals(DjiboutiSchoolsCatalog.REGION_ID, map["regionId"])
            assertEquals(DjiboutiSchoolsCatalog.REGION_NAME, map["regionName"])
            assertEquals("Djibouti-Ville", map["city"])
            assertEquals("Djibouti", map["country"])
            assertEquals(true, map["isOfficialCatalog"])
            assertEquals(true, map["isActive"])
            assertTrue((map["sector"] as String).isNotBlank())
            assertTrue((map["address"] as String).isNotBlank())
            if (entry.description.isNotBlank()) {
                assertEquals(entry.description, map["description"])
            }
        }
    }
}
