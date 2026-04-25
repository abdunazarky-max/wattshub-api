/**
 * Node.js - Firebase Admin SDK
 * COMPLETE WORKING sendNotification() FUNCTION
 */

const admin = require('firebase-admin');

/**
 * Sends a high-priority chat notification to a specific device.
 * Matches the required payload structure for chat_messages_v3 channel.
 * 
 * @param {string} fcmToken - Recipient's FCM token
 * @param {object} messageData - Object containing senderId, senderName, message, chatId, and isGroup (optional)
 */
async function sendNotification(fcmToken, messageData) {
    if (!fcmToken) return;

    const payload = {
        token: fcmToken,
        notification: {
            title: messageData.senderName || "New Message",
            body: messageData.message || "Hello from chat"
        },
        android: {
            priority: "high",
            notification: {
                channelId: "chat_messages_v6",
                sound: "default",
            }
        },
        data: {
            type: "chat",
            senderId: messageData.senderId,
            senderName: messageData.senderName,
            message: messageData.message,
            chatId: messageData.chatId,
            isGroup: messageData.isGroup ? "true" : "false",
            avatarUrl: messageData.avatarUrl || "",
            navigate_to: `chat/${messageData.senderId}/${messageData.senderName}/${messageData.isGroup ? 'true' : 'false'}`
        }
    };

    try {
        const response = await admin.messaging().send(payload);
        console.log(`[WattsHubNotify] FCM Sent Successfully:`, response);
        return response;
    } catch (error) {
        console.error(`[WattsHubNotify] FCM Error:`, error);
        throw error;
    }
}

// Example Usage:
/*
const myToken = "DEVICE_FCM_TOKEN_HERE";
const msgData = {
    senderId: "user_123",
    senderName: "Abdunazar",
    message: "Hey! This is a high priority notification.",
    chatId: "chat_456"
};
sendNotification(myToken, msgData);
*/

module.exports = { sendNotification };
