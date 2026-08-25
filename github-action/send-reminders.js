// Last-resort push sender for MedAlert.
//
// This script never sees a medication name or dosage — Firestore only ever holds an
// anonymized numeric medicationId (the app's local Room primary key) plus future
// trigger timestamps. It runs on a schedule (see ../.github/workflows/reminders.yml)
// and sends a data-only FCM push for any occurrence that:
//   - is due (scheduled time has passed)
//   - has had at least GRACE_MS to be caught by the phone's own local AlarmManager /
//     SafetyNetWorker first — this is a backstop, not the primary channel
//   - has not already been sent (tracked per-medication in Firestore)
//
// The receiving app maps medicationId back to the real medication name locally and
// re-checks its own history/audit log before showing anything, so even a duplicate or
// late send here is safe.

const admin = require('firebase-admin');

const GRACE_MS = 3 * 60 * 1000; // give local alarms 3 min head start before FCM backstop kicks in

function init() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT env var is not set');
  }
  const credentials = JSON.parse(raw);
  admin.initializeApp({ credential: admin.credential.cert(credentials) });
}

async function run() {
  init();
  const db = admin.firestore();
  const messaging = admin.messaging();
  const now = Date.now();

  const usersSnap = await db.collection('users').get();
  console.log(`Checking ${usersSnap.size} device(s)`);

  for (const userDoc of usersSnap.docs) {
    const { fcmToken } = userDoc.data();
    if (!fcmToken) continue;

    const medsSnap = await userDoc.ref.collection('medications').where('active', '==', true).get();

    for (const medDoc of medsSnap.docs) {
      const medicationId = medDoc.id;
      const data = medDoc.data();
      const occurrences = Array.isArray(data.occurrences) ? data.occurrences : [];
      const sent = Array.isArray(data.sentOccurrences) ? data.sentOccurrences : [];
      const sentSet = new Set(sent);

      const due = occurrences.filter((t) => t <= now - GRACE_MS && !sentSet.has(t));
      if (due.length === 0) continue;

      for (const scheduledTime of due) {
        try {
          await messaging.send({
            token: fcmToken,
            data: {
              medicationId: String(medicationId),
              scheduledTime: String(scheduledTime),
            },
            android: { priority: 'high' },
          });
          sentSet.add(scheduledTime);
          console.log(`Sent push: device=${userDoc.id} med=${medicationId} scheduledTime=${new Date(scheduledTime).toISOString()}`);
        } catch (err) {
          console.error(`Failed to send push for med=${medicationId}:`, err.message);
        }
      }

      // Prune sent-markers for occurrences the app no longer lists (schedule changed/rotated out),
      // so this array doesn't grow forever.
      const stillRelevant = new Set(occurrences);
      const prunedSent = Array.from(sentSet).filter((t) => stillRelevant.has(t));

      await medDoc.ref.update({ sentOccurrences: prunedSent });
    }
  }

  console.log('Done.');
}

run().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
