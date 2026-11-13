const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_nat_1";
const DIRECTOR_UID = "director_1";
const TEACHER_UID = "teacher_1";
const PARENT_A_UID = "parent_a";
const PARENT_B_UID = "parent_b";
const STUDENT_UID = "student_1";
const ATTACKER_UID = "attacker_999";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  // Using server-safe past/current Date converted by rules-unit-testing
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot list or read any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("regions").get());
  await assertFails(unauthDb.collection("schools").get());
  await assertFails(unauthDb.collection("students").get());
  await assertFails(unauthDb.collection("lessons").get());
  await assertFails(unauthDb.collection("assessments").get());
  await assertFails(unauthDb.collection("messages").get());
});

test("Email spoofing test: unverified email matching bootstrapped admin is rejected", async () => {
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "moustaphaderey@gmail.com",
      email_verified: false,
    })
    .firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("platform_config").doc("bootstrap").set({
      ownerUid: ADMIN_UID,
      createdAt: nowTs(),
    });
  });

  await assertFails(
    spoofDb.collection("users").doc("spoof_uid").set({
      userId: "spoof_uid",
      fullName: "Spoof User",
      role: "admin_national",
      isActive: true,
      consentAccepted: true,
      createdAt: nowTs(),
    })
  );
});

test("Self-escalation blocked after bootstrap exists", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("platform_config").doc("bootstrap").set({
      ownerUid: ADMIN_UID,
      createdAt: nowTs(),
    });
  });

  const attackerDb = testEnv
    .authenticatedContext(ATTACKER_UID, {
      email: "attacker@example.com",
      email_verified: true,
    })
    .firestore();

  await assertFails(
    attackerDb.collection("users").doc(ATTACKER_UID).set({
      userId: ATTACKER_UID,
      fullName: "Attacker",
      role: "admin_national",
      isActive: true,
      consentAccepted: true,
      createdAt: nowTs(),
    })
  );
});

test("Shadow update test: ghost field rejected on region creation", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "moustaphaderey@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertFails(
    adminDb.collection("regions").doc("reg_djib").set({
      name: "Djibouti-Ville",
      code: "DJ-01",
      nationalAdminUid: ADMIN_UID,
      createdByUid: ADMIN_UID,
      ghostField: true,
      createdAt: nowTs(),
    })
  );

  await assertSucceeds(
    adminDb.collection("regions").doc("reg_djib").set({
      name: "Djibouti-Ville",
      code: "DJ-01",
      nationalAdminUid: ADMIN_UID,
      createdByUid: ADMIN_UID,
      authorizedUids: [ADMIN_UID],
      createdAt: nowTs(),
    })
  );
});

test("Parent isolation: parent A can read own child lessons/grades, parent B cannot", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("classes").doc("class_6a").set({
      schoolId: "school_1",
      name: "6ème A",
      level: "6ème",
      academicYear: "2026-2027",
      directorUid: DIRECTOR_UID,
      createdByUid: DIRECTOR_UID,
      createdAt: nowTs(),
    });
    await db.collection("students").doc("stu_1").set({
      schoolId: "school_1",
      classId: "class_6a",
      firstName: "Amina",
      lastName: "Hassan",
      matricule: "DJ-2026-001",
      parentUids: [PARENT_A_UID],
      studentUid: STUDENT_UID,
      gradesVisibleToStudent: false,
      isActive: true,
      directorUid: DIRECTOR_UID,
      createdByUid: DIRECTOR_UID,
      authorizedUids: [DIRECTOR_UID, TEACHER_UID, PARENT_A_UID],
      createdAt: nowTs(),
    });
    await db.collection("lessons").doc("lesson_1").set({
      schoolId: "school_1",
      classId: "class_6a",
      subjectId: "subj_math",
      teacherUid: TEACHER_UID,
      directorUid: DIRECTOR_UID,
      lessonDate: "2026-11-13",
      chapter: "Fractions",
      title: "Addition de fractions",
      summary: "Mise au même dénominateur et exercices corrigés.",
      parentUids: [PARENT_A_UID],
      authorizedUids: [DIRECTOR_UID, TEACHER_UID, PARENT_A_UID, STUDENT_UID],
      createdAt: nowTs(),
    });
    await db.collection("assessments").doc("grade_1").set({
      schoolId: "school_1",
      classId: "class_6a",
      subjectId: "subj_math",
      studentId: "stu_1",
      studentUid: STUDENT_UID,
      gradesVisibleToStudent: false,
      parentUids: [PARENT_A_UID],
      teacherUid: TEACHER_UID,
      directorUid: DIRECTOR_UID,
      authorizedUids: [DIRECTOR_UID, TEACHER_UID, PARENT_A_UID],
      title: "Devoir 1",
      assessmentType: "devoir",
      score: 16,
      maxScore: 20,
      coefficient: 2,
      period: "Trimestre 1",
      date: "2026-11-13",
      createdAt: nowTs(),
    });
  });

  const parentADb = testEnv.authenticatedContext(PARENT_A_UID).firestore();
  const parentBDb = testEnv.authenticatedContext(PARENT_B_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();

  // Parent A succeeds with matching query
  await assertSucceeds(
    parentADb
      .collection("lessons")
      .where("parentUids", "array-contains", PARENT_A_UID)
      .get()
  );
  await assertSucceeds(
    parentADb
      .collection("assessments")
      .where("parentUids", "array-contains", PARENT_A_UID)
      .get()
  );

  // Parent B fails reading Parent A's child data
  await assertFails(parentBDb.collection("students").doc("stu_1").get());
  await assertFails(parentBDb.collection("lessons").doc("lesson_1").get());
  await assertFails(parentBDb.collection("assessments").doc("grade_1").get());
  await assertFails(parentBDb.collection("assessments").get());

  // Student fails reading grade when gradesVisibleToStudent is false
  await assertFails(studentDb.collection("assessments").doc("grade_1").get());
});

test("Audit logs are append-only: update and delete rejected", async () => {
  const actorDb = testEnv.authenticatedContext(DIRECTOR_UID).firestore();
  await assertSucceeds(
    actorDb.collection("audit_logs").doc("log_1").set({
      actorUid: DIRECTOR_UID,
      actorRole: "directeur",
      action: "CREATE_STUDENT",
      targetType: "student",
      targetId: "stu_1",
      details: "Inscription élève",
      createdAt: nowTs(),
    })
  );
  await assertFails(
    actorDb.collection("audit_logs").doc("log_1").update({ details: "tampered" })
  );
  await assertFails(
    actorDb.collection("audit_logs").doc("log_1").delete()
  );
});

test("Multi-tenant isolation: Teacher/Director of School A cannot read or write School B entities or spoof tenantId", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("regions").doc("reg_1").set({
      name: "Djibouti-Ville",
      code: "DJ01",
      nationalAdminUid: ADMIN_UID,
      createdByUid: ADMIN_UID,
      createdAt: nowTs(),
    });
    await db.collection("schools").doc("school_a").set({
      name: "École A",
      code: "EA",
      schoolType: "primaire",
      regionId: "reg_1",
      tenantId: "school_a",
      nationalAdminUid: ADMIN_UID,
      directorUid: "dir_a",
      isActive: true,
      createdByUid: ADMIN_UID,
      createdAt: nowTs(),
    });
    await db.collection("schools").doc("school_b").set({
      name: "École B",
      code: "EB",
      schoolType: "college",
      regionId: "reg_1",
      tenantId: "school_b",
      nationalAdminUid: ADMIN_UID,
      directorUid: "dir_b",
      isActive: true,
      createdByUid: ADMIN_UID,
      createdAt: nowTs(),
    });
    await db.collection("users").doc("dir_a").set({
      userId: "dir_a",
      fullName: "Directeur A",
      role: "directeur",
      schoolId: "school_a",
      tenantId: "school_a",
      isActive: true,
      consentAccepted: true,
      createdAt: nowTs(),
    });
    await db.collection("users").doc("teacher_a").set({
      userId: "teacher_a",
      fullName: "Enseignant A",
      role: "enseignant",
      schoolId: "school_a",
      tenantId: "school_a",
      isActive: true,
      consentAccepted: true,
      createdAt: nowTs(),
    });
    await db.collection("classes").doc("class_b1").set({
      schoolId: "school_b",
      tenantId: "school_b",
      name: "5ème B",
      level: "5ème",
      academicYear: "2026-2027",
      directorUid: "dir_b",
      createdByUid: "dir_b",
      createdAt: nowTs(),
    });
    await db.collection("classes").doc("class_a1").set({
      schoolId: "school_a",
      tenantId: "school_a",
      name: "CM2 A",
      level: "CM2",
      academicYear: "2026-2027",
      directorUid: "dir_a",
      createdByUid: "dir_a",
      createdAt: nowTs(),
    });
  });

  const dirADb = testEnv.authenticatedContext("dir_a").firestore();
  const teacherADb = testEnv.authenticatedContext("teacher_a").firestore();

  // Director A can create class in own tenant (school_a)
  await assertSucceeds(
    dirADb.collection("classes").doc("class_a2").set({
      schoolId: "school_a",
      tenantId: "school_a",
      name: "CM1 A",
      level: "CM1",
      academicYear: "2026-2027",
      directorUid: "dir_a",
      createdByUid: "dir_a",
      createdAt: nowTs(),
    })
  );

  // Director A cannot spoof mismatched tenantId
  await assertFails(
    dirADb.collection("classes").doc("class_spoof").set({
      schoolId: "school_a",
      tenantId: "school_b",
      name: "Spoof",
      level: "CM1",
      academicYear: "2026-2027",
      directorUid: "dir_a",
      createdByUid: "dir_a",
      createdAt: nowTs(),
    })
  );

  // Director A cannot create or read classes in School B
  await assertFails(
    dirADb.collection("classes").doc("class_b2").set({
      schoolId: "school_b",
      tenantId: "school_b",
      name: "4ème B",
      level: "4ème",
      academicYear: "2026-2027",
      directorUid: "dir_a",
      createdByUid: "dir_a",
      createdAt: nowTs(),
    })
  );
  await assertFails(dirADb.collection("classes").doc("class_b1").get());

  // Teacher A cannot publish a lesson referencing School B's classId even if claiming schoolId = school_a
  await assertFails(
    teacherADb.collection("lessons").doc("lesson_cross_tenant").set({
      schoolId: "school_a",
      tenantId: "school_a",
      classId: "class_b1",
      subjectId: "subj_1",
      teacherUid: "teacher_a",
      directorUid: "dir_a",
      lessonDate: "2026-11-13",
      chapter: "Test",
      title: "Cross-tenant test",
      summary: "Should fail because class_b1 belongs to school_b.",
      createdAt: nowTs(),
    })
  );

  // Teacher A succeeds publishing in School A's class_a1
  await assertSucceeds(
    teacherADb.collection("lessons").doc("lesson_a1").set({
      schoolId: "school_a",
      tenantId: "school_a",
      classId: "class_a1",
      subjectId: "subj_1",
      teacherUid: "teacher_a",
      directorUid: "dir_a",
      lessonDate: "2026-11-13",
      chapter: "Grammaire",
      title: "Les pronoms",
      summary: "Leçon dans l'établissement A.",
      createdAt: nowTs(),
    })
  );
});

test("Role registration access_requests: applicant submits, non-admin cannot list/approve, national admin lists & approves", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const admin = ctx.firestore();
    await admin.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      fullName: "Inspecteur National",
      role: "admin_national",
      tenantId: "national",
      isActive: true,
      consentAccepted: true,
      createdAt: nowTs(),
    });
    await admin.collection("users").doc(PARENT_A_UID).set({
      userId: PARENT_A_UID,
      fullName: "Parent Standard",
      role: "parent",
      schoolId: "school_a",
      tenantId: "school_a",
      isActive: true,
      consentAccepted: true,
      createdAt: nowTs(),
    });
  });

  const teacherDb = testEnv.authenticatedContext("new_teacher_1", { email: "newteacher@ecole.dj", email_verified: true }).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A_UID, { email: "parent@ecole.dj", email_verified: true }).firestore();
  const natAdminDb = testEnv.authenticatedContext(ADMIN_UID, { email: "nat@ecole.dj", email_verified: true }).firestore();

  // New teacher submits registration request
  await assertSucceeds(
    teacherDb.collection("access_requests").doc("new_teacher_1").set({
      userId: "new_teacher_1",
      fullName: "Hassan Ali",
      email: "newteacher@ecole.dj",
      requestedRole: "enseignant",
      schoolHint: "Lycée de Djibouti — Mathématiques",
      phone: "+25377112233",
      status: "pending",
      createdAt: nowTs(),
    })
  );

  // Non-admin cannot list or approve access_requests
  await assertFails(parentDb.collection("access_requests").get());
  await assertFails(
    parentDb.collection("access_requests").doc("new_teacher_1").update({
      status: "approved",
      reviewerUid: PARENT_A_UID,
    })
  );

  // National admin can list and approve access_requests
  await assertSucceeds(natAdminDb.collection("access_requests").get());
  await assertSucceeds(
    natAdminDb.collection("access_requests").doc("new_teacher_1").update({
      status: "approved",
      reviewerUid: ADMIN_UID,
      updatedAt: nowTs(),
    })
  );
});
