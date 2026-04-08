const admin = require("firebase-admin");
const serviceAccount = require("/home/kali/projects/my-api/serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const emailToDelete = "abdunazaky@gmail.com";

async function deleteUser() {
  try {
    const userRecord = await admin.auth().getUserByEmail(emailToDelete);
    const uid = userRecord.uid;
    
    // 1. Delete from Authentication
    await admin.auth().deleteUser(uid);
    console.log(`Successfully deleted user from Firebase Auth: ${uid}`);

    // 2. Delete from Firestore users collection
    await admin.firestore().collection('users').document(uid).delete();
    console.log(`Successfully deleted user from Firestore 'users' collection`);

    // 3. Find and delete by email field if it exists elsewhere
    const usersSnapshot = await admin.firestore().collection('users').where('email', '==', emailToDelete).get();
    for (const doc of usersSnapshot.docs) {
        await doc.ref.delete();
        console.log(`Deleted additional Firestore document: ${doc.id}`);
    }
    
    console.log("Cleanup complete!");
    process.exit(0);
  } catch (error) {
    if (error.code === 'auth/user-not-found') {
        console.log(`User ${emailToDelete} not found in Auth. Checking Firestore directly...`);
        const usersSnapshot = await admin.firestore().collection('users').where('email', '==', emailToDelete).get();
        if (usersSnapshot.empty) {
            console.log("No data found in Firestore either. The user is completely removed!");
        } else {
            for (const doc of usersSnapshot.docs) {
                await doc.ref.delete();
                console.log(`Deleted Firestore doc without Auth record: ${doc.id}`);
            }
        }
        process.exit(0);
    } else {
        console.error("Error deleting user:", error);
        process.exit(1);
    }
  }
}

deleteUser();
