package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
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

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
