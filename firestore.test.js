const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

// --- SECURITY & PERMISSION TESTS ---

test("Unauthenticated user: cannot read habits", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("habits").get());
});

test("Unauthenticated user: cannot create habit", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("habits").doc("habit_1").set({
      userId: ALICE_UID,
      title: "Meditation",
      category: "Mindfulness",
      colorHex: "#6366F1",
      iconName: "meditation",
      targetPerDay: 1,
      isArchived: false,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can create their own valid habit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("habit_1").set({
      userId: ALICE_UID,
      title: "Morning Run",
      description: "Run 3km every morning",
      category: "Fitness",
      colorHex: "#10B981",
      iconName: "run",
      frequency: "DAILY",
      targetPerDay: 1,
      unit: "times",
      reminderTime: "07:00",
      reminderEnabled: true,
      isArchived: false,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create habit with spoofed userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("habit_2").set({
      userId: BOB_UID, // Mismatch with request.auth.uid
      title: "Reading",
      category: "Learning",
      colorHex: "#F59E0B",
      iconName: "book",
      targetPerDay: 20,
      isArchived: false,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot read another user's habit", async () => {
  // Seed Alice's habit via admin context
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("habits").doc("alice_h1").set({
      userId: ALICE_UID,
      title: "Alice Secret Habit",
      category: "Health",
      colorHex: "#6366F1",
      iconName: "star",
      targetPerDay: 1,
      isArchived: false,
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("habits").doc("alice_h1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("habits").get()
  );
});

test("Authenticated user: can create and read their habit log", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habit_logs").doc("log_1").set({
      userId: ALICE_UID,
      habitId: "habit_1",
      date: "2026-11-09",
      value: 1,
      isCompleted: true,
      note: "Felt great today!",
      completedAt: new Date(),
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habit_logs").doc("log_1").get()
  );
});

test("Authenticated user: Bob cannot read Alice's habit logs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("habit_logs").doc("log_alice").set({
      userId: ALICE_UID,
      habitId: "h1",
      date: "2026-11-09",
      value: 1,
      isCompleted: true,
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("habit_logs").doc("log_alice").get()
  );
});

test("Authenticated user: can create and update user settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      dailyReminderEnabled: true,
      dailyReminderTime: "20:00",
      userName: "Alice",
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyReminderTime: "21:00",
      updatedAt: new Date(),
    })
  );
});
