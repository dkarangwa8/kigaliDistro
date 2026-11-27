const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const BAR_UID = "bar_owner_kigali_1";
const DIST_UID = "distributor_kigali_2";
const OTHER_UID = "stranger_user_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validProfilePayload(uid, role = "BAR_OWNER") {
  const ts = pastDate(5000);
  return {
    userId: uid,
    displayName: "Jean Bosco",
    role,
    businessName: role === "BAR_OWNER" ? "Chez Lando Lounge" : "Gikondo Bralirwa Hub",
    neighborhood: "Kimihurura",
    phone: "+250788123456",
    lat: -1.9536,
    lng: 30.0928,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPendingOrderPayload(barUid = BAR_UID) {
  const ts = pastDate(5000);
  return {
    barOwnerId: barUid,
    barName: "Chez Lando Lounge",
    barNeighborhood: "Remera",
    barAddressNote: "Near KG 11 Ave roundabout",
    barLat: -1.9579,
    barLng: 30.1086,
    distributorId: "",
    distributorName: "",
    depotName: "Gikondo Main Depot",
    driverLat: -1.9695,
    driverLng: 30.0748,
    itemsSummary: "4x Primus 72cl, 2x Mutzig 65cl",
    totalCrates: 6,
    totalAmountRwf: 91600,
    status: "PENDING",
    paymentMethod: "MOMO_MTN",
    paymentStatus: "UNPAID",
    paymentReference: "",
    etaMinutes: 18,
    distanceKm: 4.8,
    trafficLevel: "MODERATE",
    routeSummary: "Via KN 3 Rd & KG 11 Ave",
    recipientName: "",
    signatureCaptured: false,
    signatureSvgPath: "",
    photoVerified: false,
    photoCaption: "",
    photoThumbnailBase64: "",
    deliveryNotes: "Deliver to side terrace gate",
    createdAt: ts,
    updatedAt: ts,
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

// --- 1. UNAUTHENTICATED DENIALS ---
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(BAR_UID).get());
  await assertFails(unauth.collection("orders").where("status", "==", "PENDING").get());
  await assertFails(unauth.collection("orders").doc("order_1").set(validPendingOrderPayload()));
  await assertFails(unauth.collection("ratings").where("distributorId", "==", DIST_UID).get());
});

// --- 2. USER PROFILE PII ISOLATION ---
test("Users can create/read/update own profile; cannot read another user's profile or list users", async () => {
  const barDb = testEnv.authenticatedContext(BAR_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  await assertSucceeds(barDb.collection("users").doc(BAR_UID).set(validProfilePayload(BAR_UID)));
  await assertSucceeds(barDb.collection("users").doc(BAR_UID).get());
  await assertSucceeds(
    barDb.collection("users").doc(BAR_UID).update({
      role: "DISTRIBUTOR",
      businessName: "Remera Depot",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read and list must fail
  await assertFails(otherDb.collection("users").doc(BAR_UID).get());
  await assertFails(barDb.collection("users").get());
  // Future timestamp rejected
  await assertFails(
    barDb.collection("users").doc(BAR_UID).update({
      displayName: "Tampered",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  // Ghost field rejected (Shadow Update test)
  await assertFails(
    barDb.collection("users").doc(BAR_UID).update({
      isAdmin: true,
      updatedAt: pastDate(1000),
    })
  );
});

// --- 3. ORDER LIFECYCLE, QUERY ALIGNMENT & TERMINAL LOCK ---
test("Full order flow: Bar creates -> Distributor lists PENDING & accepts -> Live tracking -> Acceptance -> Terminal lock", async () => {
  const barDb = testEnv.authenticatedContext(BAR_UID).firestore();
  const distDb = testEnv.authenticatedContext(DIST_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  // Create pending order (NO `id` field in payload)
  await assertSucceeds(barDb.collection("orders").doc("order_kgl_1").set(validPendingOrderPayload()));

  // Extra `@DocumentId` field `id` must be rejected
  await assertFails(
    barDb.collection("orders").doc("order_kgl_bad").set({
      ...validPendingOrderPayload(),
      id: "order_kgl_bad",
    })
  );

  // Unfiltered list on orders must fail (Query Trust test)
  await assertFails(distDb.collection("orders").get());

  // Filtered queries matching Kotlin repository must succeed
  await assertSucceeds(distDb.collection("orders").where("status", "==", "PENDING").get());
  await assertSucceeds(barDb.collection("orders").where("barOwnerId", "==", BAR_UID).get());

  // Action 1: Distributor accepts order
  await assertSucceeds(
    distDb.collection("orders").doc("order_kgl_1").update({
      distributorId: DIST_UID,
      distributorName: "Eric Habimana",
      depotName: "Gikondo Main Depot",
      driverLat: -1.9695,
      driverLng: 30.0748,
      status: "ACCEPTED",
      etaMinutes: 14,
      distanceKm: 4.5,
      trafficLevel: "MODERATE",
      routeSummary: "Via KN 3 Rd -> KG 11 Ave",
      updatedAt: pastDate(2000),
    })
  );

  // Once accepted, unrelated stranger cannot read or modify it
  await assertFails(otherDb.collection("orders").doc("order_kgl_1").get());
  await assertFails(
    otherDb.collection("orders").doc("order_kgl_1").update({
      status: "EN_ROUTE",
      updatedAt: pastDate(1000),
    })
  );

  // Distributor assigned query succeeds
  await assertSucceeds(distDb.collection("orders").where("distributorId", "==", DIST_UID).get());

  // Action 2: Distributor updates live status & GPS to EN_ROUTE -> ARRIVED
  await assertSucceeds(
    distDb.collection("orders").doc("order_kgl_1").update({
      status: "EN_ROUTE",
      driverLat: -1.9620,
      driverLng: 30.0910,
      etaMinutes: 8,
      distanceKm: 2.1,
      trafficLevel: "LIGHT",
      routeSummary: "Approaching Remera via KG 11 Ave",
      updatedAt: pastDate(1500),
    })
  );

  await assertSucceeds(
    distDb.collection("orders").doc("order_kgl_1").update({
      status: "ARRIVED",
      driverLat: -1.9579,
      driverLng: 30.1086,
      etaMinutes: 0,
      distanceKm: 0.0,
      trafficLevel: "LIGHT",
      routeSummary: "Arrived at Chez Lando Lounge",
      updatedAt: pastDate(1200),
    })
  );

  // Cannot complete delivery without signature/photo/payment reference
  await assertFails(
    barDb.collection("orders").doc("order_kgl_1").update({
      status: "DELIVERED",
      paymentStatus: "UNPAID",
      signatureCaptured: false,
      photoVerified: false,
      deliveredAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Action 3: Complete Delivery Acceptance (payment + signature + photo)
  await assertSucceeds(
    distDb.collection("orders").doc("order_kgl_1").update({
      status: "DELIVERED",
      paymentMethod: "MOMO_MTN",
      paymentStatus: "PAID",
      paymentReference: "MOMO-KGL-882410",
      recipientName: "Claudine Uwase",
      signatureCaptured: true,
      signatureSvgPath: "0.10,0.50 0.25,0.42 0.55,0.60",
      photoVerified: true,
      photoCaption: "6 crates delivered intact",
      photoThumbnailBase64: "iVBORw0KGgoAAAANSUhEUg==",
      deliveryNotes: "Paid via MTN MoMo",
      deliveredAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Terminal State Locking: No further updates allowed after DELIVERED
  await assertFails(
    distDb.collection("orders").doc("order_kgl_1").update({
      status: "ARRIVED",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    barDb.collection("orders").doc("order_kgl_1").update({
      status: "CANCELLED",
      deliveryNotes: "Too late",
      updatedAt: pastDate(500),
    })
  );
});

// --- 4. ORDER EVENTS & RATINGS ---
test("Order events and post-delivery ratings enforce parent order state and participant isolation", async () => {
  const barDb = testEnv.authenticatedContext(BAR_UID).firestore();
  const distDb = testEnv.authenticatedContext(DIST_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("orders").doc("order_done_1").set({
      ...validPendingOrderPayload(),
      distributorId: DIST_UID,
      distributorName: "Eric Habimana",
      status: "DELIVERED",
      paymentStatus: "PAID",
      paymentReference: "MOMO-KGL-123456",
      recipientName: "Claudine",
      signatureCaptured: true,
      signatureSvgPath: "0.1,0.2 0.8,0.8",
      photoVerified: true,
      deliveredAt: pastDate(2000),
    });
  });

  // Event creation by participant succeeds; stranger fails
  await assertSucceeds(
    distDb.collection("orders").doc("order_done_1").collection("events").doc("evt_1").set({
      orderId: "order_done_1",
      actorId: DIST_UID,
      participantUids: [BAR_UID, DIST_UID],
      status: "DELIVERED",
      title: "Delivery Completed",
      message: "Signed and verified by Claudine",
      createdAt: pastDate(1000),
    })
  );
  await assertFails(
    otherDb.collection("orders").doc("order_done_1").collection("events").doc("evt_2").set({
      orderId: "order_done_1",
      actorId: OTHER_UID,
      participantUids: [OTHER_UID],
      status: "DELIVERED",
      title: "Fake",
      message: "Unauthorized",
      createdAt: pastDate(1000),
    })
  );

  // Participant-filtered event list succeeds; unfiltered fails
  await assertSucceeds(
    barDb.collection("orders").doc("order_done_1").collection("events")
      .where("participantUids", "array-contains", BAR_UID).get()
  );
  await assertFails(barDb.collection("orders").doc("order_done_1").collection("events").get());

  // Bar owner submits rating for DELIVERED order
  await assertSucceeds(
    barDb.collection("ratings").doc("order_done_1").set({
      orderId: "order_done_1",
      barOwnerId: BAR_UID,
      barName: "Chez Lando Lounge",
      distributorId: DIST_UID,
      distributorName: "Eric Habimana",
      stars: 5,
      punctualityStars: 5,
      handlingStars: 5,
      comment: "Fast arrival despite Remera traffic!",
      createdAt: pastDate(1000),
    })
  );

  // Distributor and bar owner can query their own ratings; stranger cannot
  await assertSucceeds(distDb.collection("ratings").where("distributorId", "==", DIST_UID).get());
  await assertFails(otherDb.collection("ratings").doc("order_done_1").get());
  await assertFails(otherDb.collection("ratings").get());

  // Rating is immutable once created
  await assertFails(barDb.collection("ratings").doc("order_done_1").update({ stars: 1 }));
});
