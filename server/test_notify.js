const admin = require('firebase-admin');
const { sendNotification } = require('./fcm_sender');

// Load service account
const serviceAccount = require('./serviceAccountKey.json');

if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
}

const targetToken = "AdrTqXGSkqTlHUx-VKnV0LGaPSZb3AX_PLmK4qnxdWITVpFKGpibp4eACGS0v-G6eYOTakKa4KIMvTPheMqDhMvOpF8iWR_n9zxUVv5IHwyMrk64GGEndpM4jAumQbyYEgowUca4XTRENRRIWWshPcFf";

const testData = {
    senderId: "test_admin",
    senderName: "WattsHub Bot",
    message: "🚀 Test Notification Successful! Your setup is working.",
    chatId: "test_chat_id",
    isGroup: false,
    avatarUrl: ""
};

console.log("Sending test notification to:", targetToken);

sendNotification(targetToken, testData)
    .then(response => {
        console.log("Success! Response:", response);
        process.exit(0);
    })
    .catch(err => {
        console.error("Failed!", err);
        process.exit(1);
    });
