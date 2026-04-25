const admin = require('firebase-admin');
const path = require('path');

// ── Validate & repair the private key before initializing ─────────────────────
const serviceAccount = require(path.join(__dirname, "serviceAccountKey.json"));

function repairPrivateKey(raw) {
  if (!raw) return raw;
  // 1. Normalise escaped newlines (literal '\n' -> real newline)
  let key = raw.replace(/\\n/g, '\n');
  // 2. Strip carriage returns that can corrupt base64 line lengths
  key = key.replace(/\r/g, '');
  // 3. Reformat PEM body with clean 64-char lines (fixes truncation/merge bugs)
  const header = '-----BEGIN PRIVATE KEY-----';
  const footer = '-----END PRIVATE KEY-----';
  const lines  = key.split('\n').filter(l => l.trim() && !l.startsWith('---'));
  const b64raw = lines.join('');
  const chunks = b64raw.match(/.{1,64}/g) || [];
  return `${header}\n${chunks.join('\n')}\n${footer}\n`;
}

const cleanKey = repairPrivateKey(serviceAccount.private_key);

admin.initializeApp({
  credential: admin.credential.cert({
    projectId:   serviceAccount.project_id,
    clientEmail: serviceAccount.client_email,
    privateKey:  cleanKey,
  }),
});

const db = admin.firestore();

async function findAndListDuplicates() {
  console.log('--- Starting Duplicates Scan ---');
  let usersSnap;
  try {
    usersSnap = await db.collection('users').get();
  } catch (error) {
    console.error('\n❌ Failed to connect to Firestore.');
    console.error('Error:', error.message);
    if (error.message.includes('DECODER') || error.message.includes('unsupported')) {
      console.error('\n🔑 Root Cause: Your serviceAccountKey.json private key is corrupted or');
      console.error('   incompatible with the current OpenSSL / Node.js version.');
      console.error('\n✅ Fix: Generate a fresh service account key:');
      console.error('   1. Open https://console.firebase.google.com');
      console.error('   2. Go to Project Settings → Service Accounts');
      console.error('   3. Click "Generate new private key"');
      console.error('   4. Replace server/serviceAccountKey.json with the new file');
      console.error('   5. Run this script again.');
    }
    return;
  }
  const emailMap = new Map();
  const phoneMap = new Map();
  const duplicates = [];

  usersSnap.forEach(doc => {
    const data = doc.data();
    const email = data.email ? data.email.toLowerCase().trim() : null;
    const phone = data.phone ? data.phone.trim() : null;
    const uid = doc.id;

    if (email) {
      if (!emailMap.has(email)) {
        emailMap.set(email, []);
      }
      emailMap.get(email).push({ uid, ...data });
    }

    if (phone) {
      if (!phoneMap.has(phone)) {
        phoneMap.set(phone, []);
      }
      phoneMap.get(phone).push({ uid, ...data });
    }
  });

  console.log("\nEmail Duplicates:");
  emailMap.forEach((users, email) => {
    if (users.length > 1) {
      console.log(`Email: ${email} (${users.length} accounts found)`);
      users.forEach((u, i) => {
        console.log(`  [${i}] UID: ${u.uid}, Name: ${u.name}, Status: ${u.status}`);
      });
      duplicates.push({ type: 'email', value: email, users });
    }
  });

  console.log("\nPhone Duplicates:");
  phoneMap.forEach((users, phone) => {
    if (users.length > 1) {
      console.log(`Phone: ${phone} (${users.length} accounts found)`);
      users.forEach((u, i) => {
        console.log(`  [${i}] UID: ${u.uid}, Name: ${u.name}, Status: ${u.status}`);
      });
      duplicates.push({ type: 'phone', value: phone, users });
    }
  });

  if (duplicates.length === 0) {
    console.log("\nNo duplicates found.");
  } else {
    console.log(`\nFound ${duplicates.length} sets of duplicates.`);
    if (process.argv.includes('--clean')) {
      console.log("\n--- Starting Cleanup ---");
      for (const set of duplicates) {
        // Keep the one with the latest status or just the first one
        const [toKeep, ...toDelete] = set.users;
        console.log(`Keeping UID: ${toKeep.uid} (${set.type}: ${set.value})`);
        
        for (const user of toDelete) {
          console.log(`  Deleting UID: ${user.uid}...`);
          await db.collection('users').doc(user.uid).delete();
        }
      }
      console.log("\nCleanup complete.");
    } else {
      console.log("Run with --clean to actually delete duplicates.");
    }
  }
}

findAndListDuplicates().catch(console.error);
