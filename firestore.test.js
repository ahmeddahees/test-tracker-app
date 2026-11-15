const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-6d520589-0a8c-4993-89c1-5f6dbb44d67e";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const DEVICE_ID = "device_pixel_01";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

function validDevicePayload(uid = ALICE_UID) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    deviceName: "Pixel 9 Pro",
    isTracking: true,
    status: "active",
    latitude: 30.0444,
    longitude: 31.2357,
    accuracyMeters: 8.5,
    speedMps: 1.2,
    altitudeMeters: 25.0,
    batteryPercent: 84,
    isCharging: false,
    updateIntervalSec: 10,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPingPayload(uid = ALICE_UID, deviceId = DEVICE_ID) {
  return {
    userId: uid,
    deviceId: deviceId,
    latitude: 30.0445,
    longitude: 31.2359,
    accuracyMeters: 6.0,
    speedMps: 2.5,
    batteryPercent: 83,
    createdAt: pastTimestamp(),
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

test("Unauthenticated user cannot read or write devices", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(db.collection(`users/${ALICE_UID}/devices`).get());
  await assertFails(
    db.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`).set(validDevicePayload())
  );
});

test("Owner can create, read, and update telemetry on their device", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const deviceRef = aliceDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`);

  await assertSucceeds(deviceRef.set(validDevicePayload()));
  await assertSucceeds(deviceRef.get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/devices`).get());

  await assertSucceeds(
    deviceRef.update({
      latitude: 30.05,
      longitude: 31.24,
      accuracyMeters: 5.0,
      speedMps: 3.1,
      altitudeMeters: 28.0,
      batteryPercent: 80,
      isCharging: true,
      isTracking: true,
      status: "alert",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Owner cannot inject ghost fields or invalid coordinates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const deviceRef = aliceDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`);

  await assertFails(
    deviceRef.set({
      ...validDevicePayload(),
      isAdmin: true,
    })
  );

  await assertFails(
    deviceRef.set({
      ...validDevicePayload(),
      latitude: 95.0,
    })
  );

  await assertSucceeds(deviceRef.set(validDevicePayload()));
  await assertFails(
    deviceRef.update({
      status: "hacked",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Cross-user access is blocked for devices and pings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`).set(validDevicePayload())
  );
  await assertSucceeds(
    aliceDb
      .doc(`users/${ALICE_UID}/devices/${DEVICE_ID}/pings/ping_1`)
      .set(validPingPayload())
  );

  await assertFails(bobDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/devices`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}/pings/ping_1`).get()
  );
  await assertFails(
    bobDb
      .doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`)
      .update({ status: "paused", updatedAt: pastTimestamp() })
  );
});

test("Pings require existing parent device and are immutable after create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const pingRef = aliceDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}/pings/ping_1`);

  // Orphan ping without parent device must fail
  await assertFails(pingRef.set(validPingPayload()));

  // Create parent device, then ping succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/devices/${DEVICE_ID}`).set(validDevicePayload())
  );
  await assertSucceeds(pingRef.set(validPingPayload()));

  // Updating an immutable ping must fail
  await assertFails(pingRef.update({ latitude: 31.0 }));
});
