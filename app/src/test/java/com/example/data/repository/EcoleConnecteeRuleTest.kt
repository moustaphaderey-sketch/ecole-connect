package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.seed.DjiboutiSchoolsSeeder
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class EcoleConnecteeRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun unauthenticatedUser_observeRegions_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = AdminRepository(firestore, auth)
        val fakeProfile = UserProfile(userId = "anon_uid", role = UserRole.ADMIN_NATIONAL.key)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeRegions(fakeProfile).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            checkNotNull(fsError) { "Expected nested FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError.code)
        }
    }

    @Test
    fun bootstrapAndCreateSchoolHierarchy_succeedsForAdmin_andIsolatesCrossUser() = runBlocking {
        val adminEmail = "admin_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val otherEmail = "other_${UUID.randomUUID().toString().take(8)}@ecole.dj"

        val adminUid = signInTestUser(adminEmail)
        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)
        val academicRepo = AcademicRepository(firestore, auth)

        val bootstrapResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.bootstrapNationalAdmin(
                uid = adminUid,
                fullName = "Inspecteur National",
                email = adminEmail,
                phone = "+25377000001",
                consentAccepted = true
            )
        }
        bootstrapResult.getOrThrow()

        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }
        assertEquals(UserRole.ADMIN_NATIONAL.key, adminProfile.role)

        val regionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createRegion(adminProfile, "Djibouti-Ville", "DJ01").getOrThrow()
        }
        val regions = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { list -> list.any { it.id == regionId } }
        }
        assertTrue(regions.any { it.id == regionId && it.name == "Djibouti-Ville" })

        val region = regions.first { it.id == regionId }
        val schoolId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createSchool(adminProfile, region, "Collège de Boulaos", "CB01", "college", "Boulaos").getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { list -> list.any { it.id == schoolId } }
        }.first { it.id == schoolId }
        assertEquals(schoolId, school.tenantId)

        val classId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createClass(adminProfile, school, "6ème A", "6ème", "2026-2027").getOrThrow()
        }
        val schoolClass = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(adminProfile).first { list -> list.any { it.id == classId } }
        }.first { it.id == classId }
        assertEquals(schoolId, schoolClass.tenantId)

        val subjectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createSubject(adminProfile, school, schoolClass, "Mathématiques", "MATH", 4.0, "", "").getOrThrow()
        }
        val subject = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSubjects(adminProfile).first { list -> list.any { it.id == subjectId } }
        }.first { it.id == subjectId }
        assertEquals(schoolId, subject.tenantId)

        val lessonId = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.publishLesson(
                actor = adminProfile,
                school = school,
                schoolClass = schoolClass,
                subject = subject,
                lessonDate = "2026-11-13",
                chapter = "Nombres décimaux",
                title = "Comparaison des nombres décimaux",
                summary = "Lecture, écriture et comparaison sur la droite graduée.",
                skillsWorked = "Comparer et ordonner des décimaux",
                classExercises = "Exercices 1 à 4 page 18",
                homework = "Exercice 5 page 19 pour demain",
                homeworkDueDate = "2026-11-14",
                attachmentLabel = "Fiche_exercices_6A.pdf"
            ).getOrThrow()
        }
        assertTrue(lessonId.isNotBlank())

        // Cross-tenant & cross-user isolation check: user/parent from another context cannot read school's data
        val otherUid = signInTestUser(otherEmail)
        val otherProfile = UserProfile(
            userId = otherUid,
            role = UserRole.DIRECTEUR.key,
            schoolId = "other_tenant_school",
            tenantId = "other_tenant_school"
        )
        val otherSchools = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(otherProfile).first()
        }
        val otherClasses = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(otherProfile).first()
        }
        val otherLessons = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeLessons(otherProfile).first()
        }
        assertTrue(otherSchools.none { it.id == schoolId })
        assertTrue(otherClasses.none { it.id == classId })
        assertTrue(otherLessons.none { it.id == lessonId })
    }

    @Test
    fun roleRegistration_submitAccessRequest_andAdminApproval_succeeds() = runBlocking {
        val adminEmail = "nat_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val teacherEmail = "prof_${UUID.randomUUID().toString().take(8)}@ecole.dj"

        val adminUid = signInTestUser(adminEmail)
        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)

        authRepo.bootstrapNationalAdmin(
            uid = adminUid,
            fullName = "Admin National",
            email = adminEmail,
            phone = "+25377000010",
            consentAccepted = true,
            seedOfficialSchools = true
        ).getOrThrow()
        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        // Teacher signs in and submits role registration request
        val teacherUid = signInTestUser(teacherEmail)
        authRepo.submitAccessRequest(
            uid = teacherUid,
            fullName = "Amina Farah",
            email = teacherEmail,
            requestedRole = UserRole.ENSEIGNANT,
            schoolHint = "Collège de Balbala — Français",
            phone = "+25377110022"
        ).getOrThrow()

        val myReq = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeMyAccessRequest(teacherUid).first { it != null }!!
        }
        assertEquals("pending", myReq.status)
        assertEquals(UserRole.ENSEIGNANT.key, myReq.requestedRole)

        // National admin observes seeded school, verifies school is mandatory, and approves request
        signInTestUser(adminEmail)
        val authorizedSchool = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { list -> list.isNotEmpty() }
        }.first()
        val requests = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(adminProfile).first { list -> list.any { it.userId == teacherUid } }
        }
        val targetReq = requests.first { it.userId == teacherUid }
        val missingSchoolResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.approveAccessRequest(adminProfile, targetReq, UserRole.ENSEIGNANT, "", null)
        }
        assertTrue(missingSchoolResult.isFailure)

        val invitationId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.approveAccessRequest(adminProfile, targetReq, UserRole.ENSEIGNANT, authorizedSchool.regionId, authorizedSchool).getOrThrow()
        }
        assertTrue(invitationId.isNotBlank())

        // Teacher finds pending invitation and claims role
        signInTestUser(teacherEmail)
        val pendingInv = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.findPendingInvitationForEmail(teacherEmail)
        }
        checkNotNull(pendingInv) { "Expected pending invitation for approved access request" }
        authRepo.claimRoleInvitation(
            uid = teacherUid,
            invitation = pendingInv,
            fullName = "Amina Farah",
            email = teacherEmail,
            phone = "+25377110022",
            consentAccepted = true
        ).getOrThrow()

        val teacherProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(teacherUid).first { it != null }!!
        }
        assertEquals(UserRole.ENSEIGNANT.key, teacherProfile.role)
    }

    @Test
    fun importOfficialDjiboutiSchools_createsRegionAndAll39SchoolsIdempotently() = runBlocking {
        val adminEmail = "imp_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val adminUid = signInTestUser(adminEmail)
        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)

        authRepo.bootstrapNationalAdmin(
            uid = adminUid,
            fullName = "Admin Import",
            email = adminEmail,
            phone = "+25377000099",
            consentAccepted = true,
            seedOfficialSchools = false
        ).getOrThrow()

        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        val seedResult = withTimeout(15000L) {
            DjiboutiSchoolsSeeder(firestore, auth).seed(
                actorUid = adminProfile.userId,
                actorName = adminProfile.fullName,
                actorRole = adminProfile.role,
                nationalAdminUid = adminProfile.userId
            ).getOrThrow()
        }
        assertTrue(seedResult.regionCreated)
        assertEquals(39, seedResult.createdCount)
        assertEquals(18, seedResult.primaryCount)
        assertEquals(17, seedResult.middleCount)
        assertEquals(4, seedResult.highCount)

        val persistedSchools = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { it.size == 39 }
        }
        assertEquals(18, persistedSchools.count { it.schoolType == "primaire" })
        assertEquals(17, persistedSchools.count { it.schoolType == "college" })
        assertEquals(4, persistedSchools.count { it.schoolType == "lycee" })
        assertTrue(persistedSchools.all { it.city == "Djibouti-Ville" && it.country == "Djibouti" && it.isOfficialCatalog && it.sector.isNotBlank() })

        val persistedRegions = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { it.isNotEmpty() }
        }
        val secondImportCount = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.importOfficialDjiboutiSchools(adminProfile, persistedRegions, persistedSchools).getOrThrow()
        }
        assertEquals(0, secondImportCount)
    }

    @Test
    fun bootstrapNationalAdmin_doesNotSeedByDefault_andExplicitSeederPopulatesAll39DjiboutiSchools() = runBlocking {
        val adminEmail = "autoseed_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val adminUid = signInTestUser(adminEmail)
        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)

        authRepo.bootstrapNationalAdmin(
            uid = adminUid,
            fullName = "Inspecteur AutoSeed",
            email = adminEmail,
            phone = "+25377000088",
            consentAccepted = true
        ).getOrThrow()

        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        val initialSchools = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first()
        }
        assertEquals(0, initialSchools.size)

        val initialRegions = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first()
        }
        assertEquals(0, initialRegions.size)

        val seedResult = withTimeout(15000L) {
            DjiboutiSchoolsSeeder(firestore, auth).seed(
                actorUid = adminProfile.userId,
                actorName = adminProfile.fullName,
                actorRole = adminProfile.role,
                nationalAdminUid = adminProfile.userId,
                existingRegions = initialRegions,
                existingSchools = initialSchools
            ).getOrThrow()
        }
        assertEquals(39, seedResult.createdCount)

        val regions = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { list -> list.any { it.id == "reg_djibouti_ville" } }
        }
        assertTrue(regions.any { it.id == "reg_djibouti_ville" && it.name == "Djibouti-Ville" })

        val schools = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { it.size == 39 }
        }
        assertEquals(18, schools.count { it.schoolType == "primaire" })
        assertEquals(17, schools.count { it.schoolType == "college" })
        assertEquals(4, schools.count { it.schoolType == "lycee" })

        val rerun = withTimeout(DEFAULT_TIMEOUT_MS) {
            DjiboutiSchoolsSeeder(firestore, auth).seed(
                actorUid = adminProfile.userId,
                actorName = adminProfile.fullName,
                actorRole = adminProfile.role,
                nationalAdminUid = adminProfile.userId,
                existingRegions = regions,
                existingSchools = schools
            ).getOrThrow()
        }
        assertEquals(0, rerun.createdCount)
        assertEquals(39, rerun.skippedCount)
    }

    @Test
    fun directorWithoutPreassignedSchool_canCreateSchoolAndClass_andApprovedDirectorAutoLinksSchool() = runBlocking {
        val adminEmail = "nat_dir_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val dir1Email = "dir1_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val dir2Email = "dir2_${UUID.randomUUID().toString().take(8)}@ecole.dj"

        val adminUid = signInTestUser(adminEmail)
        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)

        authRepo.bootstrapNationalAdmin(
            uid = adminUid,
            fullName = "Inspecteur National",
            email = adminEmail,
            phone = "+25377000011",
            consentAccepted = true,
            seedOfficialSchools = true
        ).getOrThrow()
        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        // Case 1: Director 1 submits request; approving without an authorized school is rejected, then approved with official school
        val dir1Uid = signInTestUser(dir1Email)
        authRepo.submitAccessRequest(
            uid = dir1Uid,
            fullName = "Directeur Hassan",
            email = dir1Email,
            requestedRole = UserRole.DIRECTEUR,
            schoolId = "dj_ep_pk12",
            schoolName = "École PK12",
            schoolHint = "École PK12 (Primaire)",
            phone = "+25377220011"
        ).getOrThrow()

        signInTestUser(adminEmail)
        val pk12School = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { list -> list.any { it.id == "dj_ep_pk12" } }
        }.first { it.id == "dj_ep_pk12" }
        val req1 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(adminProfile).first { list -> list.any { it.userId == dir1Uid } }
        }.first { it.userId == dir1Uid }
        val missingSchoolApproval = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.approveAccessRequest(adminProfile, req1, UserRole.DIRECTEUR, "", null)
        }
        assertTrue(missingSchoolApproval.isFailure)

        adminRepo.approveAccessRequest(adminProfile, req1, UserRole.DIRECTEUR, pk12School.regionId, pk12School).getOrThrow()

        signInTestUser(dir1Email)
        val inv1 = withTimeout(DEFAULT_TIMEOUT_MS) { authRepo.findPendingInvitationForEmail(dir1Email) }!!
        authRepo.claimRoleInvitation(dir1Uid, inv1, "Directeur Hassan", dir1Email, "+25377220011", true).getOrThrow()
        val dir1UpdatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(dir1Uid).first { it?.schoolId == "dj_ep_pk12" }!!
        }
        val dir1Schools = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(dir1UpdatedProfile).first { it.any { s -> s.id == "dj_ep_pk12" } }
        }
        val dir1School = dir1Schools.first { it.id == "dj_ep_pk12" }
        val classId1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createClass(dir1UpdatedProfile, dir1School, "CM2 A", "CM2", "2026-2027").getOrThrow()
        }
        assertTrue(classId1.isNotBlank())

        // Case 2: Admin creates a school and approves Director 2 WITH that school pre-selected
        signInTestUser(adminEmail)
        val regions = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { it.isNotEmpty() }
        }
        val school2Id = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createSchool(adminProfile, regions.first(), "Collège de Boulaos", "COL-BL", "college", "Boulaos").getOrThrow()
        }
        val school2 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { it.any { s -> s.id == school2Id } }
        }.first { it.id == school2Id }

        val dir2Uid = signInTestUser(dir2Email)
        authRepo.submitAccessRequest(
            uid = dir2Uid,
            fullName = "Directrice Kadija",
            email = dir2Email,
            requestedRole = UserRole.DIRECTEUR,
            schoolHint = "Collège de Boulaos",
            phone = "+25377330022"
        ).getOrThrow()

        signInTestUser(adminEmail)
        val req2 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(adminProfile).first { list -> list.any { it.userId == dir2Uid } }
        }.first { it.userId == dir2Uid }
        adminRepo.approveAccessRequest(adminProfile, req2, UserRole.DIRECTEUR, school2.regionId, school2).getOrThrow()

        signInTestUser(dir2Email)
        val inv2 = withTimeout(DEFAULT_TIMEOUT_MS) { authRepo.findPendingInvitationForEmail(dir2Email) }!!
        authRepo.claimRoleInvitation(dir2Uid, inv2, "Directrice Kadija", dir2Email, "+25377330022", true).getOrThrow()
        val dir2Profile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(dir2Uid).first { it != null }!!
        }
        val dir2Schools = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(dir2Profile).first { it.any { s -> s.id == school2Id } }
        }
        val dir2School = dir2Schools.first { it.id == school2Id }
        val classId2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createClass(dir2Profile, dir2School, "6ème B", "6ème", "2026-2027").getOrThrow()
        }
        assertTrue(classId2.isNotBlank())
    }

    @Test
    fun updateAllRecordTypes_succeedsForAuthorizedUsers_andDeniesUnauthorized() = runBlocking {
        val adminEmail = "upd_adm_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val dirEmail = "upd_dir_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val teacherEmail = "upd_prof_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val parentEmail = "upd_par_${UUID.randomUUID().toString().take(8)}@ecole.dj"

        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)
        val academicRepo = AcademicRepository(firestore, auth)
        val commRepo = CommunicationRepository(firestore, auth)

        // 1. Bootstrap national admin (without seeding all 39 schools for speed)
        val adminUid = signInTestUser(adminEmail)
        authRepo.bootstrapNationalAdmin(
            uid = adminUid,
            fullName = "Admin Édition",
            email = adminEmail,
            phone = "+25377000100",
            consentAccepted = true,
            seedOfficialSchools = false
        ).getOrThrow()
        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        // 2. Create & Update Region (Record 1)
        val regId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createRegion(adminProfile, "Djibouti Nord", "DJN").getOrThrow()
        }
        val region = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { list -> list.any { it.id == regId } }
        }.first { it.id == regId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateRegion(adminProfile, region, "Djibouti Centre", "DJC").getOrThrow()
        }
        val updatedRegion = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { list -> list.any { it.id == regId && it.name == "Djibouti Centre" } }
        }.first { it.id == regId }
        assertEquals("DJC", updatedRegion.code)

        // 3. Create & Update School (Record 2)
        val schoolId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createSchool(adminProfile, updatedRegion, "École Initiale", "EI-01", "primaire", "Plateau").getOrThrow()
        }
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { list -> list.any { it.id == schoolId } }
        }.first { it.id == schoolId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateSchool(
                actor = adminProfile,
                school = school,
                region = updatedRegion,
                name = "École Modifiée",
                code = "EM-02",
                schoolType = "college",
                address = "Boulevard de la République",
                sector = "Centre-ville",
                description = "Établissement rénové",
                isActive = true
            ).getOrThrow()
        }
        val updatedSchool = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { list -> list.any { it.id == schoolId && it.name == "École Modifiée" } }
        }.first { it.id == schoolId }
        assertEquals("EM-02", updatedSchool.code)
        assertEquals("college", updatedSchool.schoolType)

        // 4. Create & Update Class (Record 3)
        val classId1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createClass(adminProfile, updatedSchool, "6ème A", "6ème", "2025-2026").getOrThrow()
        }
        val classId2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createClass(adminProfile, updatedSchool, "5ème A", "5ème", "2026-2027").getOrThrow()
        }
        val class1 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(adminProfile).first { list -> list.any { it.id == classId1 } }
        }.first { it.id == classId1 }
        val class2 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(adminProfile).first { list -> list.any { it.id == classId2 } }
        }.first { it.id == classId2 }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateClass(adminProfile, class1, "6ème Excellence", "6ème Bilingue", "2026-2027").getOrThrow()
        }
        val updatedClass1 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(adminProfile).first { list -> list.any { it.id == classId1 && it.name == "6ème Excellence" } }
        }.first { it.id == classId1 }
        assertEquals("2026-2027", updatedClass1.academicYear)

        // 5. Provision Director, Teacher, Parent + Update Invitation (Record 7) & User (Record 8)
        val invPendingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createRoleInvitation(
                actor = adminProfile,
                email = "pending_edit@ecole.dj",
                fullName = "Invité Initial",
                role = UserRole.ENSEIGNANT,
                regionId = updatedRegion.id,
                school = updatedSchool,
                phone = "+25377001122"
            ).getOrThrow()
        }
        val pendingInv = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeInvitations(adminProfile).first { list -> list.any { it.id == invPendingId } }
        }.first { it.id == invPendingId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateRoleInvitation(adminProfile, pendingInv, "Invité Modifié", "+25377998877", "revoked").getOrThrow()
        }
        val updatedInv = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeInvitations(adminProfile).first { list -> list.any { it.id == invPendingId && it.status == "revoked" } }
        }.first { it.id == invPendingId }
        assertEquals("Invité Modifié", updatedInv.fullName)

        // Create & claim director, teacher, parent invitations
        adminRepo.createRoleInvitation(adminProfile, dirEmail, "Directeur Initial", UserRole.DIRECTEUR, updatedRegion.id, updatedSchool, "+25377000201").getOrThrow()
        adminRepo.createRoleInvitation(adminProfile, teacherEmail, "Prof Initial", UserRole.ENSEIGNANT, updatedRegion.id, updatedSchool, "+25377000202").getOrThrow()
        adminRepo.createRoleInvitation(adminProfile, parentEmail, "Parent Initial", UserRole.PARENT, updatedRegion.id, updatedSchool, "+25377000203").getOrThrow()

        val dirUid = signInTestUser(dirEmail)
        authRepo.claimRoleInvitation(dirUid, authRepo.findPendingInvitationForEmail(dirEmail)!!, "Directeur Initial", dirEmail, "+25377000201", true).getOrThrow()

        val teacherUid = signInTestUser(teacherEmail)
        authRepo.claimRoleInvitation(teacherUid, authRepo.findPendingInvitationForEmail(teacherEmail)!!, "Prof Initial", teacherEmail, "+25377000202", true).getOrThrow()

        val parentUid = signInTestUser(parentEmail)
        authRepo.claimRoleInvitation(parentUid, authRepo.findPendingInvitationForEmail(parentEmail)!!, "Parent Initial", parentEmail, "+25377000203", true).getOrThrow()

        // Admin updates Managed User (Record 8)
        signInTestUser(adminEmail)
        val managedTeacher = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeManagedUsers(adminProfile).first { list -> list.any { it.userId == teacherUid } }
        }.first { it.userId == teacherUid }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateManagedUser(adminProfile, managedTeacher, "Professeure Halima", UserRole.ENSEIGNANT, updatedSchool, true).getOrThrow()
        }
        val updatedTeacher = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeManagedUsers(adminProfile).first { list -> list.any { it.userId == teacherUid && it.fullName == "Professeure Halima" } }
        }.first { it.userId == teacherUid }
        assertEquals("Professeure Halima", updatedTeacher.fullName)

        // 6. Create & Update Subject (Record 4) and ScheduleEntry (Record 5)
        val subjId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createSubject(adminProfile, updatedSchool, updatedClass1, "Maths", "MTH", 3.0, teacherUid, updatedTeacher.fullName).getOrThrow()
        }
        val subj = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSubjects(adminProfile).first { list -> list.any { it.id == subjId } }
        }.first { it.id == subjId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateSubject(adminProfile, subj, "Mathématiques Appliquées", "MATH", 4.5, teacherUid, updatedTeacher.fullName).getOrThrow()
        }
        val updatedSubj = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSubjects(adminProfile).first { list -> list.any { it.id == subjId && it.name == "Mathématiques Appliquées" } }
        }.first { it.id == subjId }
        assertEquals(4.5, updatedSubj.coefficient, 0.01)

        val schedId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createScheduleEntry(adminProfile, updatedSchool, updatedClass1, updatedSubj, "lundi", "08:00", "09:00", "Salle 1").getOrThrow()
        }
        val sched = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchedules(adminProfile).first { list -> list.any { it.id == schedId } }
        }.first { it.id == schedId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateScheduleEntry(adminProfile, sched, updatedSubj, "mardi", "09:15", "10:15", "Salle B2").getOrThrow()
        }
        val updatedSched = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchedules(adminProfile).first { list -> list.any { it.id == schedId && it.dayOfWeek == "mardi" } }
        }.first { it.id == schedId }
        assertEquals("Salle B2", updatedSched.room)

        // 7. Enroll & Update Student (Record 6)
        val studentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.enrollStudent(
                actor = adminProfile,
                school = updatedSchool,
                schoolClass = updatedClass1,
                firstName = "Youssouf",
                lastName = "Ali",
                matricule = "DJ-001",
                dateOfBirth = "2013-04-12",
                gender = "M",
                parentUids = listOf(parentUid),
                studentUid = "",
                gradesVisibleToStudent = true
            ).getOrThrow()
        }
        val student = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeStudents(adminProfile).first { list -> list.any { it.id == studentId } }
        }.first { it.id == studentId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateStudent(
                actor = adminProfile,
                student = student,
                targetClass = class2,
                firstName = "Youssouf",
                lastName = "Ali-Robleh",
                matricule = "DJ-001B",
                dateOfBirth = "2013-04-13",
                gender = "M",
                gradesVisibleToStudent = false,
                isActive = true
            ).getOrThrow()
        }
        val updatedStudent = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeStudents(adminProfile).first { list -> list.any { it.id == studentId && it.lastName == "Ali-Robleh" } }
        }.first { it.id == studentId }
        assertEquals(class2.id, updatedStudent.classId)
        assertEquals("DJ-001B", updatedStudent.matricule)

        // 8. Teacher creates & updates Lesson (Record 9), Assessment (Record 10), Attendance (Record 12), Difficulty (Record 13)
        signInTestUser(teacherEmail)
        val teacherProfileNow = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(teacherUid).first { it != null }!!
        }
        val lessonId = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.publishLesson(
                actor = teacherProfileNow,
                school = updatedSchool,
                schoolClass = updatedClass1,
                subject = updatedSubj,
                lessonDate = "2026-11-10",
                chapter = "Chapitre 1",
                title = "Fractions simples",
                summary = "Résumé initial de la leçon.",
                skillsWorked = "Additionner",
                classExercises = "Ex 1",
                homework = "Ex 2",
                homeworkDueDate = "2026-11-12",
                attachmentLabel = ""
            ).getOrThrow()
        }
        val lesson = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeLessons(teacherProfileNow).first { list -> list.any { it.id == lessonId } }
        }.first { it.id == lessonId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateLesson(
                actor = teacherProfileNow,
                lesson = lesson,
                lessonDate = "2026-11-11",
                chapter = "Chapitre 1 bis",
                title = "Fractions irréductibles",
                summary = "Résumé mis à jour avec exemples détaillés.",
                skillsWorked = "Simplifier et comparer",
                classExercises = "Ex 1 à 5 p.22",
                homework = "Ex 6 p.23",
                homeworkDueDate = "2026-11-14",
                attachmentLabel = "Fiche_rev.pdf"
            ).getOrThrow()
        }
        val updatedLesson = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeLessons(teacherProfileNow).first { list -> list.any { it.id == lessonId && it.title == "Fractions irréductibles" } }
        }.first { it.id == lessonId }
        assertEquals("Fiche_rev.pdf", updatedLesson.attachmentLabel)

        val gradeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.recordAssessment(teacherProfileNow, updatedStudent, updatedSubj, "Devoir 1", "devoir", 14.0, 20.0, 2.0, "Trimestre 1", "2026-11-10", "Bien").getOrThrow()
        }
        val grade = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeAssessments(teacherProfileNow).first { list -> list.any { it.id == gradeId } }
        }.first { it.id == gradeId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateAssessment(teacherProfileNow, grade, "Devoir 1 corrigé", "composition", 16.5, 20.0, 3.0, "Trimestre 1", "2026-11-11", "Très belle progression").getOrThrow()
        }
        val updatedGrade = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeAssessments(teacherProfileNow).first { list -> list.any { it.id == gradeId && it.score == 16.5 } }
        }.first { it.id == gradeId }
        assertEquals("composition", updatedGrade.assessmentType)

        val attId = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.recordAttendance(teacherProfileNow, updatedStudent, "2026-11-10", "absent", 0, false, "Non signalée").getOrThrow()
        }
        val att = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeAttendance(teacherProfileNow).first { list -> list.any { it.id == attId } }
        }.first { it.id == attId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateAttendance(teacherProfileNow, att, "2026-11-10", "retard", 15, true, "Billet médical présenté").getOrThrow()
        }
        val updatedAtt = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeAttendance(teacherProfileNow).first { list -> list.any { it.id == attId && it.status == "retard" } }
        }.first { it.id == attId }
        assertTrue(updatedAtt.justified)
        assertEquals(15, updatedAtt.minutesLate)

        val diffId = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.reportDifficulty(teacherProfileNow, updatedStudent, updatedSubj, "attention", "attention", "Bavardages en cours", "Placer devant").getOrThrow()
        }
        val diff = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeDifficulties(teacherProfileNow).first { list -> list.any { it.id == diffId } }
        }.first { it.id == diffId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateDifficulty(teacherProfileNow, diff, "methode", "info", "Prise de notes améliorée", "Continuer les encouragements", true).getOrThrow()
        }
        val updatedDiff = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeDifficulties(teacherProfileNow).first { list -> list.any { it.id == diffId && it.resolved } }
        }.first { it.id == diffId }
        assertEquals("methode", updatedDiff.category)

        // 9. Admin creates & updates ReportCard (Record 11) and Announcement (Record 14)
        signInTestUser(adminEmail)
        val reportId = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.generateReportCard(adminProfile, updatedSchool, updatedStudent, "Trimestre 1", 15.0, 12.5, "4e/30", 1, 1, "Tableau d'honneur", "Bon trimestre", false).getOrThrow()
        }
        val report = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeReportCards(adminProfile).first { list -> list.any { it.id == reportId } }
        }.first { it.id == reportId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateReportCard(adminProfile, report, 16.25, 13.0, "2e/30", 0, 1, "Félicitations", "Excellent travail confirmé", true).getOrThrow()
        }
        val updatedReport = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeReportCards(adminProfile).first { list -> list.any { it.id == reportId && it.published } }
        }.first { it.id == reportId }
        assertEquals(16.25, updatedReport.overallAverage, 0.01)
        assertEquals("Félicitations", updatedReport.councilDecision)

        val annId = withTimeout(DEFAULT_TIMEOUT_MS) {
            commRepo.publishAnnouncement(adminProfile, "school", updatedRegion.id, updatedSchool.id, "Réunion parents", "Samedi 8h.", "normale", listOf(teacherUid, parentUid)).getOrThrow()
        }
        val ann = withTimeout(FLOW_TIMEOUT_MS) {
            commRepo.observeAnnouncements(adminProfile).first { list -> list.any { it.id == annId } }
        }.first { it.id == annId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            commRepo.updateAnnouncement(adminProfile, ann, "Réunion parents reportée", "Dimanche 9h en salle polyvalente.", "importante").getOrThrow()
        }
        val updatedAnn = withTimeout(FLOW_TIMEOUT_MS) {
            commRepo.observeAnnouncements(adminProfile).first { list -> list.any { it.id == annId && it.priority == "importante" } }
        }.first { it.id == annId }
        assertEquals("Réunion parents reportée", updatedAnn.title)

        // 10. Negative Security Check: Parent cannot modify Assessment, Lesson, or Student
        signInTestUser(parentEmail)
        val parentProfileNow = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(parentUid).first { it != null }!!
        }
        val parentGradeEdit = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateAssessment(parentProfileNow, updatedGrade, "Falsifié", "devoir", 20.0, 20.0, 1.0, "Trimestre 1", "2026-11-11", "")
        }
        assertTrue(parentGradeEdit.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, parentGradeEdit.exceptionOrNull()?.unwrapFirestoreException()?.code)

        val parentLessonEdit = withTimeout(DEFAULT_TIMEOUT_MS) {
            academicRepo.updateLesson(parentProfileNow, updatedLesson, "2026-11-11", "Hack", "Hack", "Hack", "", "", "", "", "")
        }
        assertTrue(parentLessonEdit.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, parentLessonEdit.exceptionOrNull()?.unwrapFirestoreException()?.code)

        val parentStudentEdit = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateStudent(parentProfileNow, updatedStudent, null, "Hack", "Hack", "X", "", "M", true, true)
        }
        assertTrue(parentStudentEdit.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, parentStudentEdit.exceptionOrNull()?.unwrapFirestoreException()?.code)
    }

    @Test
    fun tieredAccountValidation_andStructuralLinkages_workEndToEnd() = runBlocking {
        val natAdminEmail = "tier_nat_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val dirEmail = "tier_dir_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val otherDirEmail = "tier_dir2_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val parentEmail = "tier_par_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val teacherEmail = "tier_prof_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val rejectedEmail = "tier_rej_${UUID.randomUUID().toString().take(8)}@ecole.dj"

        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)
        val academicRepo = AcademicRepository(firestore, auth)

        // 1. Bootstrap National Admin (seeds official schools + school_directory)
        val natAdminUid = signInTestUser(natAdminEmail)
        authRepo.bootstrapNationalAdmin(
            uid = natAdminUid,
            fullName = "Inspecteur National",
            email = natAdminEmail,
            phone = "+25377000900",
            consentAccepted = true,
            seedOfficialSchools = true
        ).getOrThrow()
        val natAdminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(natAdminUid).first { it != null }!!
        }

        // 2. Unactivated Director reads school_directory and submits request (approved by National Admin)
        val dirUid = signInTestUser(dirEmail)
        val directory = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeSchoolDirectory().first { list -> list.any { it.schoolId == "dj_col_boulaos" } }
        }
        val boulaosEntry = directory.first { it.schoolId == "dj_col_boulaos" }
        authRepo.submitAccessRequest(
            uid = dirUid,
            firstName = "Oumar",
            lastName = "Farah",
            fullName = "Oumar Farah",
            email = dirEmail,
            requestedRole = UserRole.DIRECTEUR,
            schoolId = boulaosEntry.schoolId,
            schoolName = boulaosEntry.name,
            schoolHint = "${boulaosEntry.name} (Collège)",
            phone = "+25377111222"
        ).getOrThrow()

        // Other director submits request for a different school
        val otherDirUid = signInTestUser(otherDirEmail)
        authRepo.submitAccessRequest(
            uid = otherDirUid,
            firstName = "Khadija",
            lastName = "Obsieh",
            fullName = "Khadija Obsieh",
            email = otherDirEmail,
            requestedRole = UserRole.DIRECTEUR,
            schoolId = "dj_ep_pk12",
            schoolName = "École PK12",
            schoolHint = "École PK12 (Primaire)",
            phone = "+25377111333"
        ).getOrThrow()

        // National Admin approves Director for Collège de Boulaos
        signInTestUser(natAdminEmail)
        val boulaosSchool = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(natAdminProfile).first { list -> list.any { it.id == "dj_col_boulaos" } }
        }.first { it.id == "dj_col_boulaos" }
        val dirReq = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(natAdminProfile).first { list -> list.any { it.userId == dirUid } }
        }.first { it.userId == dirUid }
        adminRepo.approveAccessRequest(
            actor = natAdminProfile,
            request = dirReq,
            assignedRole = UserRole.DIRECTEUR,
            regionId = boulaosSchool.regionId,
            school = boulaosSchool,
            decisionNote = "Nomination confirmée"
        ).getOrThrow()

        // Director claims invitation and creates Class, Subject, and Student in their school
        signInTestUser(dirEmail)
        val dirInv = withTimeout(DEFAULT_TIMEOUT_MS) { authRepo.findPendingInvitationForEmail(dirEmail) }!!
        authRepo.claimRoleInvitation(dirUid, dirInv, "Oumar Farah", dirEmail, "+25377111222", true).getOrThrow()
        val dirProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(dirUid).first { it?.schoolId == "dj_col_boulaos" }!!
        }
        val dirSchool = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(dirProfile).first { list -> list.any { it.id == "dj_col_boulaos" } }
        }.first { it.id == "dj_col_boulaos" }
        assertEquals(dirUid, dirSchool.directorUid)

        val classId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createClass(dirProfile, dirSchool, "6ème C", "6ème", "2026-2027").getOrThrow()
        }
        val schoolClass = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(dirProfile).first { list -> list.any { it.id == classId } }
        }.first { it.id == classId }
        val subjectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createSubject(dirProfile, dirSchool, schoolClass, "Sciences Physiques", "PC", 2.0, "", "").getOrThrow()
        }
        val studentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.enrollStudent(
                actor = dirProfile,
                school = dirSchool,
                schoolClass = schoolClass,
                firstName = "Samira",
                lastName = "Idriss",
                matricule = "DJ-6C-01",
                dateOfBirth = "2014-05-20",
                gender = "F",
                parentUids = emptyList(),
                studentUid = "",
                gradesVisibleToStudent = true
            ).getOrThrow()
        }

        // Director must NOT see other director's request (otherDirUid requested DIRECTEUR for dj_ep_pk12)
        val dirVisibleRequests = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(dirProfile).first()
        }
        assertTrue(dirVisibleRequests.none { it.userId == otherDirUid })

        // 3. Parent signs up -> stays pending -> blocked from school data -> Director requests info -> Parent resubmits -> Director approves linked to student
        val parentUid = signInTestUser(parentEmail)
        authRepo.submitAccessRequest(
            uid = parentUid,
            firstName = "Halima",
            lastName = "Idriss",
            fullName = "Halima Idriss",
            email = parentEmail,
            requestedRole = UserRole.PARENT,
            schoolId = dirSchool.id,
            schoolName = dirSchool.name,
            schoolHint = "${dirSchool.name} (Collège)",
            extraDetails = "Parent de Samira Idriss",
            phone = "+25377445566"
        ).getOrThrow()

        // Step 2 check: Pending user has NO access to school classes/students
        val pendingClassesCheck = withTimeout(FLOW_TIMEOUT_MS) {
            try {
                firestore.collection("classes").whereEqualTo("schoolId", dirSchool.id).get().await()
                false
            } catch (_: Exception) {
                true
            }
        }
        assertTrue("Pending account must not access school data", pendingClassesCheck)

        // Step 3 & 4: Director requests complementary info on Parent request
        signInTestUser(dirEmail)
        val parentReq = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(dirProfile).first { list -> list.any { it.userId == parentUid && it.status == "pending" } }
        }.first { it.userId == parentUid }
        adminRepo.requestMoreInfoAccessRequest(
            actor = dirProfile,
            request = parentReq,
            note = "Veuillez indiquer le matricule de votre fille."
        ).getOrThrow()

        // Parent sees info_requested and resubmits -> status returns to pending
        signInTestUser(parentEmail)
        val infoReq = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeMyAccessRequest(parentUid).first { it?.status == "info_requested" }!!
        }
        assertEquals("Veuillez indiquer le matricule de votre fille.", infoReq.decisionNote)
        authRepo.resubmitAccessRequest(
            uid = parentUid,
            firstName = "Halima",
            lastName = "Idriss",
            fullName = "Halima Idriss",
            requestedRole = UserRole.PARENT,
            schoolId = dirSchool.id,
            schoolName = dirSchool.name,
            schoolHint = "${dirSchool.name} (Collège)",
            extraDetails = "Parent de Samira Idriss",
            applicantResponse = "Matricule DJ-6C-01 en 6ème C",
            phone = "+25377445566"
        ).getOrThrow()

        // Director approves Parent linked to studentId
        signInTestUser(dirEmail)
        val parentReqResubmitted = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(dirProfile).first { list -> list.any { it.userId == parentUid && it.status == "pending" } }
        }.first { it.userId == parentUid }
        assertEquals("Matricule DJ-6C-01 en 6ème C", parentReqResubmitted.applicantResponse)

        adminRepo.approveAccessRequest(
            actor = dirProfile,
            request = parentReqResubmitted,
            assignedRole = UserRole.PARENT,
            regionId = dirSchool.regionId,
            school = dirSchool,
            studentIds = listOf(studentId),
            decisionNote = "Lien parent-élève vérifié"
        ).getOrThrow()

        // Step 5: Parent claims invitation -> verifies studentIds & student.parentUids linkage
        signInTestUser(parentEmail)
        val parentInv = withTimeout(DEFAULT_TIMEOUT_MS) { authRepo.findPendingInvitationForEmail(parentEmail) }!!
        assertEquals(listOf(studentId), parentInv.studentIds)
        authRepo.claimRoleInvitation(parentUid, parentInv, "Halima Idriss", parentEmail, "+25377445566", true).getOrThrow()
        val parentProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(parentUid).first { it != null }!!
        }
        assertTrue(parentProfile.studentIds.contains(studentId))
        val linkedStudents = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeStudents(parentProfile).first { list -> list.any { it.id == studentId } }
        }
        assertTrue(linkedStudents.first { it.id == studentId }.parentUids.contains(parentUid))

        // 4. Teacher signs up -> Director approves linked to classId & subjectId -> verifies linkages
        val teacherUid = signInTestUser(teacherEmail)
        authRepo.submitAccessRequest(
            uid = teacherUid,
            firstName = "Moussa",
            lastName = "Guelleh",
            fullName = "Moussa Guelleh",
            email = teacherEmail,
            requestedRole = UserRole.ENSEIGNANT,
            schoolId = dirSchool.id,
            schoolName = dirSchool.name,
            schoolHint = "${dirSchool.name} (Collège)",
            extraDetails = "6ème C — Sciences Physiques",
            phone = "+25377556677"
        ).getOrThrow()

        signInTestUser(dirEmail)
        val teacherReq = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(dirProfile).first { list -> list.any { it.userId == teacherUid } }
        }.first { it.userId == teacherUid }
        adminRepo.approveAccessRequest(
            actor = dirProfile,
            request = teacherReq,
            assignedRole = UserRole.ENSEIGNANT,
            regionId = dirSchool.regionId,
            school = dirSchool,
            classIds = listOf(classId),
            subjectIds = listOf(subjectId),
            decisionNote = "Affecté en 6ème C"
        ).getOrThrow()

        signInTestUser(teacherEmail)
        val teacherInv = withTimeout(DEFAULT_TIMEOUT_MS) { authRepo.findPendingInvitationForEmail(teacherEmail) }!!
        authRepo.claimRoleInvitation(teacherUid, teacherInv, "Moussa Guelleh", teacherEmail, "+25377556677", true).getOrThrow()
        val teacherProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(teacherUid).first { it != null }!!
        }
        assertTrue(teacherProfile.classIds.contains(classId))
        assertTrue(teacherProfile.subjectIds.contains(subjectId))

        val teacherSubjects = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSubjects(teacherProfile).first { list -> list.any { it.id == subjectId } }
        }
        assertEquals(teacherUid, teacherSubjects.first { it.id == subjectId }.teacherUid)

        // 5. Rejected applicant flow
        val rejectedUid = signInTestUser(rejectedEmail)
        authRepo.submitAccessRequest(
            uid = rejectedUid,
            firstName = "Inconnu",
            lastName = "Test",
            fullName = "Inconnu Test",
            email = rejectedEmail,
            requestedRole = UserRole.PARENT,
            schoolId = dirSchool.id,
            schoolName = dirSchool.name,
            schoolHint = "${dirSchool.name} (Collège)",
            phone = "+25377999999"
        ).getOrThrow()

        signInTestUser(dirEmail)
        val rejReq = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(dirProfile).first { list -> list.any { it.userId == rejectedUid } }
        }.first { it.userId == rejectedUid }
        adminRepo.rejectAccessRequest(dirProfile, rejReq, "Élève introuvable dans nos registres").getOrThrow()

        signInTestUser(rejectedEmail)
        val rejState = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeMyAccessRequest(rejectedUid).first { it?.status == "rejected" }!!
        }
        assertEquals("Élève introuvable dans nos registres", rejState.decisionNote)

        // 6. National Admin has national visibility across classes, students, and all requests
        signInTestUser(natAdminEmail)
        val nationalClasses = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(natAdminProfile).first { list -> list.any { it.id == classId } }
        }
        val nationalStudents = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeStudents(natAdminProfile).first { list -> list.any { it.id == studentId } }
        }
        val nationalRequests = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(natAdminProfile).first { list ->
                list.any { it.userId == otherDirUid } && list.any { it.userId == parentUid }
            }
        }
        assertTrue(nationalClasses.any { it.id == classId })
        assertTrue(nationalStudents.any { it.id == studentId })
        assertTrue(nationalRequests.size >= 4)
    }

    @Test
    fun connectedDevices_teacherAttendance_andExtendedSchoolStudentBatch_workEndToEnd() = runBlocking {
        val adminEmail = "ext_adm_${UUID.randomUUID().toString().take(8)}@ecole.dj"
        val teacherEmail = "ext_prof_${UUID.randomUUID().toString().take(8)}@ecole.dj"

        val authRepo = AuthRepository(firestore, auth)
        val adminRepo = AdminRepository(firestore, auth)
        val academicRepo = AcademicRepository(firestore, auth)
        val devicesRepo = ConnectedDevicesRepository(firestore, auth)

        val adminUid = signInTestUser(adminEmail)
        authRepo.bootstrapNationalAdmin(
            uid = adminUid,
            fullName = "Inspecteur National",
            email = adminEmail,
            phone = "+25377000800",
            consentAccepted = true
        ).getOrThrow()
        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        val regionId = adminRepo.createRegion(adminProfile, "Balbala", "BAL").getOrThrow()
        val region = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeRegions(adminProfile).first { it.any { r -> r.id == regionId } }
        }.first { it.id == regionId }

        val schoolId = adminRepo.createSchool(
            actor = adminProfile,
            region = region,
            name = "Lycée de Hodan 4",
            code = "LYC-H4",
            schoolType = "lycee",
            address = "Quartier Hodan",
            phone = "+25321351200",
            email = "contact@hodan4.dj"
        ).getOrThrow()
        val school = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeSchools(adminProfile).first { it.any { s -> s.id == schoolId } }
        }.first { it.id == schoolId }
        assertEquals("+25321351200", school.phone)
        assertEquals("contact@hodan4.dj", school.email)

        val classId = adminRepo.createClass(adminProfile, school, "2nde A", "2nde", "2026-2027").getOrThrow()
        val schoolClass = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeClasses(adminProfile).first { it.any { c -> c.id == classId } }
        }.first { it.id == classId }

        // Batch import students with guardian and academic history metadata
        val importedCount = adminRepo.importStudentsBatch(
            actor = adminProfile,
            school = school,
            classes = listOf(schoolClass),
            fallbackClass = schoolClass,
            rows = listOf(
                com.example.util.CsvStudentRow(
                    matricule = "DJ-H4-01",
                    firstName = "Fathia",
                    lastName = "Abdi",
                    dateOfBirth = "2010-03-14",
                    gender = "F",
                    className = "2nde A",
                    academicYear = "2026-2027",
                    legalGuardianName = "Abdi Moussa",
                    legalGuardianPhone = "+25377889900",
                    legalGuardianRelation = "Père",
                    academicHistory = "Collège Hodan 1 (2025-2026)"
                )
            )
        ).getOrThrow()
        assertEquals(1, importedCount)
        val importedStudent = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeStudents(adminProfile).first { it.any { s -> s.matricule == "DJ-H4-01" } }
        }.first { it.matricule == "DJ-H4-01" }
        assertEquals("Abdi Moussa", importedStudent.legalGuardianName)
        assertEquals("Collège Hodan 1 (2025-2026)", importedStudent.academicHistory)

        // Provision a teacher and test Teacher Attendance CRUD
        adminRepo.createRoleInvitation(adminProfile, teacherEmail, "Professeur Farah", UserRole.ENSEIGNANT, regionId, school, "+25377554433").getOrThrow()
        val teacherUid = signInTestUser(teacherEmail)
        val inv = authRepo.findPendingInvitationForEmail(teacherEmail)!!
        authRepo.claimRoleInvitation(teacherUid, inv, "Professeur Farah", teacherEmail, "+25377554433", true).getOrThrow()
        val teacherProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(teacherUid).first { it != null }!!
        }

        signInTestUser(adminEmail)
        val tattId = academicRepo.recordTeacherAttendance(
            actor = adminProfile,
            school = school,
            teacher = teacherProfile,
            date = "2026-11-15",
            status = "absent",
            minutesLate = 0,
            justified = false,
            reason = "Absence matinale"
        ).getOrThrow()
        val tatt = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeTeacherAttendance(adminProfile).first { it.any { r -> r.id == tattId } }
        }.first { it.id == tattId }
        academicRepo.updateTeacherAttendance(adminProfile, tatt, "2026-11-15", "retard", 15, true, "Justifié").getOrThrow()
        val updatedTatt = withTimeout(FLOW_TIMEOUT_MS) {
            academicRepo.observeTeacherAttendance(adminProfile).first { it.any { r -> r.id == tattId && r.status == "retard" } }
        }.first { it.id == tattId }
        assertTrue(updatedTatt.justified)
        academicRepo.deleteTeacherAttendance(adminProfile, updatedTatt).getOrThrow()

        // Connected Devices & QR pairing request test via emulator REST seed of pending qr_pairing_requests
        val qrReqId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        seedQrPairingRequestInEmulator(qrReqId, "Firefox", "Windows 11")

        val inspected = withTimeout(DEFAULT_TIMEOUT_MS) {
            devicesRepo.fetchPairingRequest(qrReqId).getOrThrow()
        }
        assertEquals("Firefox", inspected.browserName)
        val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            devicesRepo.approvePairingRequest(adminProfile, inspected).getOrThrow()
        }
        val activeDevices = withTimeout(FLOW_TIMEOUT_MS) {
            devicesRepo.observeConnectedDevices(adminUid).first { it.any { d -> d.sessionId == sessionId && !d.revoked } }
        }
        assertEquals(1, activeDevices.count { !it.revoked })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            devicesRepo.revokeDeviceSession(adminProfile, activeDevices.first { it.sessionId == sessionId }).getOrThrow()
        }
        val afterRevoke = withTimeout(FLOW_TIMEOUT_MS) {
            devicesRepo.observeConnectedDevices(adminUid).first { it.any { d -> d.sessionId == sessionId && d.revoked } }
        }
        assertTrue(afterRevoke.first { it.sessionId == sessionId }.revoked)
    }

    private fun seedQrPairingRequestInEmulator(requestId: String, browser: String, os: String) {
        val nowMs = System.currentTimeMillis()
        val createdIso = java.time.Instant.ofEpochMilli(nowMs - 1000).toString()
        val expiresIso = java.time.Instant.ofEpochMilli(nowMs + 120_000).toString()
        val url = java.net.URL("http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents/qr_pairing_requests?documentId=$requestId")
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer owner")
            conn.doOutput = true
            val body = """
                {
                  "fields": {
                    "requestId": {"stringValue": "$requestId"},
                    "proofHash": {"stringValue": "${"c".repeat(64)}"},
                    "status": {"stringValue": "pending"},
                    "browserName": {"stringValue": "$browser"},
                    "osName": {"stringValue": "$os"},
                    "userAgentLabel": {"stringValue": "$browser sur $os"},
                    "consumed": {"booleanValue": false},
                    "expiresAt": {"timestampValue": "$expiresIso"},
                    "createdAt": {"timestampValue": "$createdIso"},
                    "updatedAt": {"timestampValue": "$createdIso"}
                  }
                }
            """.trimIndent()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            check(code in 200..299) { "Seeding qr_pairing_requests failed with HTTP $code" }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
