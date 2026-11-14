package com.example.data.seed

import com.example.data.catalog.DjiboutiSchoolsCatalog
import com.example.data.catalog.OfficialSchoolEntry
import com.example.data.model.Region
import com.example.data.model.School
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class SchoolSeedResult(
    val regionId: String,
    val regionCreated: Boolean,
    val createdCount: Int,
    val skippedCount: Int,
    val primaryCount: Int,
    val middleCount: Int,
    val highCount: Int
)

/**
 * Initialise dans Firebase Firestore la région Djibouti-Ville et le référentiel
 * complet des 39 établissements scolaires publics (18 primaires, 17 collèges, 4 lycées).
 */
class DjiboutiSchoolsSeeder(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth? = null
) {

    suspend fun seed(
        actorUid: String,
        actorName: String,
        actorRole: String,
        nationalAdminUid: String = actorUid,
        existingRegions: List<Region> = emptyList(),
        existingSchools: List<School> = emptyList()
    ): Result<SchoolSeedResult> = runCatching {
        val natUid = nationalAdminUid.ifBlank { actorUid }
        val resolvedRegionPair = ensureDjiboutiRegion(
            actorUid = actorUid,
            actorName = actorName,
            actorRole = actorRole,
            natUid = natUid,
            existingRegions = existingRegions
        )
        val targetRegion = resolvedRegionPair.first
        val regionCreated = resolvedRegionPair.second

        // Récupérer les écoles déjà présentes en base pour garantir une idempotence stricte
        val persistedSchools = if (existingSchools.isNotEmpty()) {
            existingSchools
        } else {
            runCatching {
                db.collection("schools")
                    .whereEqualTo("nationalAdminUid", natUid)
                    .get()
                    .await()
                    .toObjects(School::class.java)
            }.getOrDefault(emptyList())
        }

        val missingEntries = DjiboutiSchoolsCatalog.allSchools.filter { entry ->
            persistedSchools.none { existing ->
                existing.id == entry.id ||
                    existing.code.equals(entry.code, ignoreCase = true) ||
                    existing.name.equals(entry.name, ignoreCase = true)
            }
        }

        if (missingEntries.isNotEmpty()) {
            val authUids = listOfNotNull(
                actorUid,
                natUid.takeIf { it.isNotBlank() },
                targetRegion.regionalAdminUid.takeIf { it.isNotBlank() }
            ).distinct()

            missingEntries.chunked(200).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { entry ->
                    val payload = buildSchoolDocumentMap(
                        entry = entry,
                        region = targetRegion,
                        actorUid = actorUid,
                        nationalAdminUid = natUid,
                        authorizedUids = authUids
                    )
                    batch.set(db.collection("schools").document(entry.id), payload)
                }
                batch.commit().await()
            }

            writeAuditLog(
                actorUid = actorUid,
                actorName = actorName,
                actorRole = actorRole,
                nationalAdminUid = natUid,
                action = "SEED_DJIBOUTI_SCHOOLS",
                targetType = "school",
                targetId = targetRegion.id,
                details = "Initialisation Firestore : ${missingEntries.size} établissement(s) de Djibouti-Ville (${missingEntries.count { it.schoolType == "primaire" }} primaires, ${missingEntries.count { it.schoolType == "college" }} collèges, ${missingEntries.count { it.schoolType == "lycee" }} lycées)."
            )
        }

        SchoolSeedResult(
            regionId = targetRegion.id,
            regionCreated = regionCreated,
            createdCount = missingEntries.size,
            skippedCount = DjiboutiSchoolsCatalog.allSchools.size - missingEntries.size,
            primaryCount = missingEntries.count { it.schoolType == "primaire" },
            middleCount = missingEntries.count { it.schoolType == "college" },
            highCount = missingEntries.count { it.schoolType == "lycee" }
        )
    }

    private suspend fun ensureDjiboutiRegion(
        actorUid: String,
        actorName: String,
        actorRole: String,
        natUid: String,
        existingRegions: List<Region>
    ): Pair<Region, Boolean> {
        val inMemoryMatch = existingRegions.firstOrNull {
            it.id == DjiboutiSchoolsCatalog.REGION_ID ||
                it.code.equals(DjiboutiSchoolsCatalog.REGION_CODE, ignoreCase = true) ||
                it.name.equals(DjiboutiSchoolsCatalog.REGION_NAME, ignoreCase = true)
        }
        if (inMemoryMatch != null) return inMemoryMatch to false

        val regionRef = db.collection("regions").document(DjiboutiSchoolsCatalog.REGION_ID)
        val snap = runCatching { regionRef.get().await() }.getOrNull()
        if (snap != null && snap.exists()) {
            val existing = snap.toObject(Region::class.java) ?: Region(
                id = DjiboutiSchoolsCatalog.REGION_ID,
                name = snap.getString("name").orEmpty().ifBlank { DjiboutiSchoolsCatalog.REGION_NAME },
                code = snap.getString("code").orEmpty().ifBlank { DjiboutiSchoolsCatalog.REGION_CODE },
                nationalAdminUid = snap.getString("nationalAdminUid").orEmpty().ifBlank { natUid },
                regionalAdminUid = snap.getString("regionalAdminUid").orEmpty(),
                createdByUid = snap.getString("createdByUid").orEmpty().ifBlank { actorUid }
            )
            return existing to false
        }

        val regionPayload = mapOf(
            "name" to DjiboutiSchoolsCatalog.REGION_NAME,
            "code" to DjiboutiSchoolsCatalog.REGION_CODE,
            "nationalAdminUid" to natUid,
            "createdByUid" to actorUid,
            "authorizedUids" to listOfNotNull(actorUid, natUid.takeIf { it.isNotBlank() }).distinct(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        regionRef.set(regionPayload).await()

        writeAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorRole = actorRole,
            nationalAdminUid = natUid,
            action = "CREATE_REGION",
            targetType = "region",
            targetId = DjiboutiSchoolsCatalog.REGION_ID,
            details = "Initialisation région ${DjiboutiSchoolsCatalog.REGION_NAME} (${DjiboutiSchoolsCatalog.REGION_CODE})"
        )

        return Region(
            id = DjiboutiSchoolsCatalog.REGION_ID,
            name = DjiboutiSchoolsCatalog.REGION_NAME,
            code = DjiboutiSchoolsCatalog.REGION_CODE,
            nationalAdminUid = natUid,
            createdByUid = actorUid
        ) to true
    }

    private suspend fun writeAuditLog(
        actorUid: String,
        actorName: String,
        actorRole: String,
        nationalAdminUid: String,
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ) {
        val logId = UUID.randomUUID().toString()
        val payload = mapOf(
            "actorUid" to actorUid,
            "actorName" to actorName,
            "actorRole" to actorRole,
            "action" to action,
            "targetType" to targetType,
            "targetId" to targetId,
            "tenantId" to "national",
            "nationalAdminUid" to nationalAdminUid,
            "details" to details,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("audit_logs").document(logId).set(payload).await()
    }

    companion object {
        fun buildSchoolDocumentMap(
            entry: OfficialSchoolEntry,
            region: Region,
            actorUid: String,
            nationalAdminUid: String,
            authorizedUids: List<String>,
            useServerTimestamps: Boolean = true
        ): Map<String, Any> {
            val timestampValue: Any = if (useServerTimestamps) {
                FieldValue.serverTimestamp()
            } else {
                com.google.firebase.Timestamp.now()
            }
            val payload = mutableMapOf<String, Any>(
                "name" to entry.name.take(160),
                "code" to entry.code.take(30),
                "schoolType" to entry.schoolType,
                "regionId" to region.id,
                "tenantId" to entry.id,
                "regionName" to region.name.take(120),
                "address" to entry.address.take(240),
                "sector" to entry.sector.take(120),
                "city" to "Djibouti-Ville",
                "country" to "Djibouti",
                "isOfficialCatalog" to true,
                "nationalAdminUid" to nationalAdminUid,
                "authorizedUids" to authorizedUids,
                "isActive" to true,
                "createdByUid" to actorUid,
                "createdAt" to timestampValue,
                "updatedAt" to timestampValue
            )
            if (entry.description.isNotBlank()) {
                payload["description"] = entry.description.take(300)
            }
            if (region.regionalAdminUid.isNotBlank()) {
                payload["regionalAdminUid"] = region.regionalAdminUid
            }
            return payload
        }
    }
}
