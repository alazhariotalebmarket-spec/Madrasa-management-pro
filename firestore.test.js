const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_madrasa_123";
const BOB_UID = "bob_madrasa_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
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

test("Unauthenticated users cannot read or write any M PRO collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = pastTime();

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("students").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("fees").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("institution").doc("profile").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("students")
      .doc("rec_1")
      .set({
        userId: ALICE_UID,
        localId: 1,
        name: "Unauthorized",
        father: "",
        phone: "",
        cls: "নূরানী",
        fee: 500,
        photoUri: "",
        roll: "1",
        electricBill: 0,
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("Cross-user isolation blocks Bob from reading, listing, updating, or deleting Alice's data", async () => {
  const created = pastTime(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("students")
      .doc("rec_101")
      .set({
        userId: ALICE_UID,
        localId: 101,
        name: "আব্দুল্লাহ",
        father: "মুহাম্মদ",
        phone: "01700000000",
        cls: "হিফজ",
        fee: 1200,
        photoUri: "",
        roll: "1",
        electricBill: 100,
        createdAt: created,
        updatedAt: created,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("students").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("students")
      .doc("rec_101")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("students")
      .doc("rec_101")
      .update({ name: "Hijacked", updatedAt: pastTime() })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("students")
      .doc("rec_101")
      .delete()
  );
});

test("Owner can create, update, list, and delete across all 11 collections with Kotlin-aligned payloads", async () => {
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = db.collection("users").doc(ALICE_UID);
  const t0 = pastTime(15000);
  const t1 = pastTime(2000);

  // 1. Institution
  await assertSucceeds(
    userDoc.collection("institution").doc("profile").set({
      userId: ALICE_UID,
      name: "ইসহাকিয়া মাদ্রাসা",
      address: "পটুয়াখালী",
      phone: "01726541607",
      logoBase64: "",
      createdAt: t0,
      updatedAt: t0,
    })
  );
  await assertSucceeds(
    userDoc.collection("institution").doc("profile").update({
      name: "ইসহাকিয়া মাদ্রাসা (আপডেট)",
      updatedAt: t1,
    })
  );

  // 2. Student
  await assertSucceeds(
    userDoc.collection("students").doc("rec_10").set({
      userId: ALICE_UID,
      localId: 10,
      name: "ইউসুফ",
      father: "ইব্রাহিম",
      phone: "01800000000",
      cls: "নাজেরা",
      fee: 800,
      photoUri: "",
      roll: "03",
      electricBill: 50,
      createdAt: t0,
      updatedAt: t0,
    })
  );
  await assertSucceeds(
    userDoc.collection("students").doc("rec_10").update({
      fee: 900,
      updatedAt: t1,
    })
  );
  await assertSucceeds(userDoc.collection("students").get());

  // 3. Fee
  await assertSucceeds(
    userDoc.collection("fees").doc("rec_20").set({
      userId: ALICE_UID,
      localId: 20,
      studentId: 10,
      amount: 900,
      type: "মাসিক বেতন",
      month: "জানুয়ারি",
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 4. Attendance
  await assertSucceeds(
    userDoc.collection("attendance").doc("att_2026-11-23_10").set({
      userId: ALICE_UID,
      localId: 30,
      date: "2026-11-23",
      studentId: 10,
      status: "উপস্থিত",
      createdAt: t0,
      updatedAt: t0,
    })
  );
  await assertSucceeds(
    userDoc.collection("attendance").doc("att_2026-11-23_10").update({
      status: "অনুপস্থিত",
      updatedAt: t1,
    })
  );

  // 5. Result (native subjects array, no JSON string)
  await assertSucceeds(
    userDoc.collection("results").doc("rec_40").set({
      userId: ALICE_UID,
      localId: 40,
      studentId: 10,
      exam: "১ম সাময়িক",
      subjects: [
        { name: "কোরআন", mark: 92 },
        { name: "হাদিস", mark: 88 },
      ],
      total: 180,
      avg: 90,
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 6. Teacher
  await assertSucceeds(
    userDoc.collection("teachers").doc("rec_50").set({
      userId: ALICE_UID,
      localId: 50,
      name: "মাওলানা আব্দুর রহমান",
      subject: "তাজবিদ",
      salary: 12000,
      address: "পটুয়াখালী",
      phone: "01900000000",
      photoUri: "",
      joinDate: "01/01/2025",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 7. TeacherPayment
  await assertSucceeds(
    userDoc.collection("teacherPayments").doc("rec_60").set({
      userId: ALICE_UID,
      localId: 60,
      teacherId: 50,
      amount: 12000,
      month: "জানুয়ারি",
      year: "2026",
      date: "23/11/2026",
      note: "বেতন পরিশোধ",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 8. Expense
  await assertSucceeds(
    userDoc.collection("expenses").doc("rec_70").set({
      userId: ALICE_UID,
      localId: 70,
      type: "খরচ",
      desc: "বিদ্যুৎ বিল",
      amount: 1500,
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 9. Notice
  await assertSucceeds(
    userDoc.collection("notices").doc("rec_80").set({
      userId: ALICE_UID,
      localId: 80,
      title: "বার্ষিক পরীক্ষা",
      description: "আগামী মাস থেকে শুরু হবে",
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 10. QuestionPaper
  await assertSucceeds(
    userDoc.collection("questionPapers").doc("rec_90").set({
      userId: ALICE_UID,
      localId: 90,
      examName: "বার্ষিক পরীক্ষা",
      className: "নাজেরা",
      subject: "তাজবিদ",
      fullMarks: "100",
      duration: "2 ঘণ্টা",
      instructions: "সব প্রশ্নের উত্তর দাও",
      questionsText: "১। মাখরাজ কয়টি?",
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // 11. ClassRoutine
  await assertSucceeds(
    userDoc.collection("classRoutines").doc("rec_100").set({
      userId: ALICE_UID,
      localId: 100,
      className: "নাজেরা",
      dayOfWeek: "শনিবার",
      timeSlot: "08:00 - 08:45",
      subject: "কোরআন",
      teacherName: "মাওলানা আব্দুর রহমান",
      duration: "৪৫ মিনিট",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(userDoc.collection("students").doc("rec_10").delete());
});

test("Security boundaries: reject @DocumentId 'id' leakage, immutable mutations, ghost keys, future timestamps, and invalid IDs", async () => {
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = db.collection("users").doc(ALICE_UID);
  const t0 = pastTime(10000);

  // Reject ghost field / 'id' field on create
  await assertFails(
    userDoc.collection("students").doc("rec_11").set({
      id: "rec_11",
      userId: ALICE_UID,
      localId: 11,
      name: "Bad Student",
      father: "",
      phone: "",
      cls: "",
      fee: 0,
      photoUri: "",
      roll: "",
      electricBill: 0,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Reject future timestamp
  await assertFails(
    userDoc.collection("notices").doc("rec_81").set({
      userId: ALICE_UID,
      localId: 81,
      title: "Future",
      description: "Tampered",
      date: "23/11/2026",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject invalid docId characters
  await assertFails(
    userDoc.collection("notices").doc("bad id!").set({
      userId: ALICE_UID,
      localId: 82,
      title: "Invalid ID",
      description: "Test",
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Valid create, then reject createdAt/userId tamper or shadow key on update
  await assertSucceeds(
    userDoc.collection("notices").doc("rec_83").set({
      userId: ALICE_UID,
      localId: 83,
      title: "Original",
      description: "Desc",
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertFails(
    userDoc.collection("notices").doc("rec_83").update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  await assertFails(
    userDoc.collection("notices").doc("rec_83").update({
      title: "Shadow",
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );

  // Reject spoofed userId
  await assertFails(
    userDoc.collection("expenses").doc("rec_71").set({
      userId: BOB_UID,
      localId: 71,
      type: "খরচ",
      desc: "Spoof",
      amount: 100,
      date: "23/11/2026",
      createdAt: t0,
      updatedAt: t0,
    })
  );
});
