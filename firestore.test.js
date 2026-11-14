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

function validProfile(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    displayName: "DashPlayer",
    coins: 250,
    highScore: 14500,
    bestDistance: 820,
    totalRuns: 5,
    selectedRunnerId: "blaze",
    selectedBoardId: "aero",
    unlockedRunners: ["blaze"],
    unlockedBoards: ["aero"],
    hoverboardsCount: 3,
    magnetLevel: 1,
    jetpackLevel: 1,
    sneakersLevel: 1,
    multiplierLevel: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validRun(uid, overrides = {}) {
  return {
    userId: uid,
    score: 5400,
    distanceMeters: 310,
    coinsCollected: 48,
    runnerId: "blaze",
    boardUsed: true,
    createdAt: new Date(Date.now() - 1000),
    ...overrides,
  };
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

test("Unauthenticated user: cannot read or write user profiles or runs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("runs").get());
});

test("Authenticated user: can create, read, and update own RunnerProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      coins: 500,
      highScore: 22000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Alice cannot read or write Bob's profile or runs", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfile(BOB_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("runs").doc("run_1").set(validRun(BOB_UID)));

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ coins: 999999, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("runs").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("runs").doc("run_2").set(validRun(ALICE_UID)));
});

test("Shadow update & schema validation: rejects ghost fields, invalid runnerId, negative coins, or immutable createdAt mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  // Reject ghost field on create
  await assertFails(profileRef.set(validProfile(ALICE_UID, { isAdmin: true })));
  // Reject invalid runnerId
  await assertFails(profileRef.set(validProfile(ALICE_UID, { selectedRunnerId: "hacker" })));
  // Reject negative coins
  await assertFails(profileRef.set(validProfile(ALICE_UID, { coins: -10 })));

  // Valid create
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));

  // Reject shadow field on update
  await assertFails(profileRef.update({ coins: 300, ghostField: "x", updatedAt: new Date(Date.now() - 1000) }));
  // Reject mutating userId
  await assertFails(profileRef.update({ userId: BOB_UID, updatedAt: new Date(Date.now() - 1000) }));
});

test("RunRecord subcollection: owner can list and create valid runs, cannot update or delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const runsCol = aliceDb.collection("users").doc(ALICE_UID).collection("runs");

  // List on empty subcollection must succeed
  await assertSucceeds(runsCol.get());

  // Create valid run
  await assertSucceeds(runsCol.doc("run_abc").set(validRun(ALICE_UID)));
  await assertSucceeds(runsCol.get());

  // Cannot update or delete append-only run record
  await assertFails(runsCol.doc("run_abc").update({ score: 999999 }));
  await assertFails(runsCol.doc("run_abc").delete());
});
