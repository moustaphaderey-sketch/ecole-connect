package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.seed.DjiboutiSchoolsSeeder
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
            consentAccepted = true
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

        // National admin observes and approves request, generating role invitation
        signInTestUser(adminEmail)
        val requests = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(adminProfile).first { list -> list.any { it.userId == teacherUid } }
        }
        val targetReq = requests.first { it.userId == teacherUid }
        val invitationId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.approveAccessRequest(adminProfile, targetReq, UserRole.ENSEIGNANT, "", null).getOrThrow()
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
    fun bootstrapNationalAdmin_automaticallySeedsAll39DjiboutiSchools() = runBlocking {
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
            consentAccepted = true
        ).getOrThrow()
        val adminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(adminUid).first { it != null }!!
        }

        // Case 1: Director 1 submits request, Admin approves WITHOUT selecting a school
        val dir1Uid = signInTestUser(dir1Email)
        authRepo.submitAccessRequest(
            uid = dir1Uid,
            fullName = "Directeur Hassan",
            email = dir1Email,
            requestedRole = UserRole.DIRECTEUR,
            schoolHint = "École Primaire PK12",
            phone = "+25377220011"
        ).getOrThrow()

        signInTestUser(adminEmail)
        val req1 = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAccessRequests(adminProfile).first { list -> list.any { it.userId == dir1Uid } }
        }.first { it.userId == dir1Uid }
        adminRepo.approveAccessRequest(adminProfile, req1, UserRole.DIRECTEUR, "", null).getOrThrow()

        signInTestUser(dir1Email)
        val inv1 = withTimeout(DEFAULT_TIMEOUT_MS) { authRepo.findPendingInvitationForEmail(dir1Email) }!!
        authRepo.claimRoleInvitation(dir1Uid, inv1, "Directeur Hassan", dir1Email, "+25377220011", true).getOrThrow()
        val dir1InitialProfile = withTimeout(FLOW_TIMEOUT_MS) {
            authRepo.observeUserProfile(dir1Uid).first { it != null }!!
        }

        val createdSchoolId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createOrClaimSchoolForDirector(
                actor = dir1InitialProfile,
                region = null,
                name = "École PK12",
                code = "EP-PK12",
                schoolType = "primaire",
                address = "Balbala PK12",
                officialSchoolId = "dj_ep_pk12"
            ).getOrThrow()
        }
        assertEquals("dj_ep_pk12", createdSchoolId)

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

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
