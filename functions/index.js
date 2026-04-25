const functions = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const cors = require("cors")({ origin: true });
const { google } = require("googleapis");

admin.initializeApp();

// Initialize Play Integrity API
const playIntegrity = google.playintegrity("v1");

/**
 * ── CLOUD FUNCTION: VERIFY INTEGRITY ──────────
 * Verifies Play Integrity Token with Google.
 */
exports.verifyIntegrity = functions.https.onRequest((req, res) => {
    return cors(req, res, async () => {
        if (req.method !== "POST") {
            return res.status(405).send("Method Not Allowed");
        }

        const { token } = req.body;
        if (!token) {
            return res.status(400).json({ status: "error", message: "Integrity token is required" });
        }

        try {
            // Obtain an authenticated client
            const auth = new google.auth.GoogleAuth({
                scopes: ["https://www.googleapis.com/auth/playintegrity"]
            });
            const authClient = await auth.getClient();

            // Decode the token
            const response = await playIntegrity.tokens.decode({
                packageName: "com.hyzin.wattshub", // Updated to match new applicationId
                requestBody: {
                    integrityToken: token
                },
                auth: authClient
            });

            const result = response.data;
            
            // Log for debugging
            console.log("Integrity Verdict:", JSON.stringify(result.tokenPayloadExternal));

            return res.status(200).json({
                status: "success",
                verdict: result.tokenPayloadExternal
            });

        } catch (error) {
            console.error("❌ [INTEGRITY ERROR] Verification failed:", error);
            return res.status(500).json({ status: "error", message: error.message });
        }
    });
});

/**
 * ── CLOUD FUNCTION: SEND OTP ─────────────────
 * Handles both Email and SMS simulation.
 * URL: https://[region]-[project-id].cloudfunctions.net/sendOTP
 */
exports.sendOTP = functions.https.onRequest((req, res) => {
    // Enable CORS for cross-origin mobile app requests
    return cors(req, res, async () => {
        if (req.method !== "POST") {
            return res.status(405).send("Method Not Allowed");
        }

        const { to, email, otp, type } = req.body;
        const target = to || email;

        if (!target || !otp) {
            console.warn("⚠️ [OTP ERROR] Missing target or otp:", req.body);
            return res.status(400).json({ status: "error", message: "Target and OTP are required" });
        }

        try {
            if (type === "sms") {
                // SMS SIMULATION (Firebase logs appear 24/7 in Google Cloud Console)
                console.log("-----------------------------------------");
                console.log(`📱 SMS SIMULATION to ${target}:`);
                console.log(`Your WattsHub code is: ${otp}`);
                console.log("-----------------------------------------");
                return res.status(200).json({ status: "success", message: "SMS Simulated" });
            } else {
                // REAL EMAIL: Sending using NodeMailer (Secure)
                // IMPORTANT: You must set these using `firebase functions:config:set` 
                // or environment variables in Google Cloud.
                const user = process.env.EMAIL_USER || functions.config().email?.user;
                const pass = process.env.EMAIL_PASS || functions.config().email?.pass;

                if (!user || !pass) {
                    console.error("❌ Email configuration missing. Set EMAIL_USER and EMAIL_PASS.");
                    return res.status(500).json({ status: "error", message: "Server email config missing" });
                }

                const transporter = nodemailer.createTransport({
                    service: "gmail",
                    auth: { user, pass }
                });

                const mailOptions = {
                    from: `"WattsHub Verification" <${user}>`,
                    to: target,
                    subject: "Your WattsHub Verification Code",
                    html: `
                        <div style="font-family: sans-serif; background: #0F0F0F; color: #FFFFFF; padding: 40px; border-radius: 20px; border: 1px solid #00FF9D; max-width: 500px; margin: auto;">
                            <h2 style="color: #00FF9D; margin-bottom: 20px;">WattsHub Verification</h2>
                            <p style="font-size: 16px; margin-bottom: 10px;">Hello!</p>
                            <p style="font-size: 14px; margin-bottom: 30px; color: #CCCCCC;">Use the code below to verify your account. It will expire in 10 minutes.</p>
                            <div style="background: #1E1E1E; padding: 20px; border-radius: 12px; text-align: center;">
                                <span style="font-size: 32px; font-weight: bold; letter-spacing: 12px; color: #FFFFFF;">${otp}</span>
                            </div>
                            <p style="font-size: 12px; margin-top: 40px; color: #888888; text-align: center;">If you didn't request this, please ignore this email.</p>
                            <p style="font-size: 12px; font-weight: bold; color: #00FF9D; text-align: center; margin-top: 10px;">SECURE ACCESS • WATTS HUB</p>
                        </div>
                    `
                };

                await transporter.sendMail(mailOptions);
                console.log(`✅ [OTP] Successfully delivered to ${target}`);
                return res.status(200).json({ status: "success", message: "OTP sent" });
            }
        } catch (error) {
            console.error("❌ [OTP ERROR] Delivery failure:", error);
            return res.status(500).json({ status: "error", message: error.message });
        }
    });
});

/**
 * ── CLOUD FUNCTION: ON MESSAGE SENT ──────────
 * Triggers when a new message is created in any chat.
 * Sends push notification to the receiver.
 */
exports.onMessageSent = functions.firestore
    .document("messages/{chatId}/contents/{messageId}")
    .onCreate(async (snapshot, context) => {
        const message = snapshot.data();
        const receiverId = message.receiver_id;
        const senderId = message.sender_id;
        const text = message.text || "Sent a media file";

        if (!receiverId) return null;

        try {
            // 1. Get receiver's FCM token
            const userDoc = await admin.firestore().collection("users").doc(receiverId).get();
            const userData = userDoc.data();
            const fcmToken = userData ? userData.fcmToken : null;

            if (!fcmToken) {
                console.log(`No FCM token for user ${receiverId}. Skipping notification.`);
                return null;
            }

            // 2. Get sender's info for the notification
            const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
            const senderData = senderDoc.data();
            const senderName = senderData ? senderData.name : "New Message";
            const senderAvatar = senderData ? senderData.profilePicUrl : "";

            // 3. Construct the FCM message (V2)
            const payload = {
                data: {
                    type: "chat",
                    senderId: senderId,
                    senderName: senderName,
                    message: text,
                    body: text, // Fallback for some clients
                    chatId: context.params.chatId,
                    avatarUrl: senderAvatar || "",
                    isGroup: "false"
                },
                android: {
                    priority: "high",
                    notification: {
                        channelId: "chat_messages_v3",
                        sound: "default"
                    }
                },
                token: fcmToken
            };

            // 4. Send the notification
            await admin.messaging().send(payload);
            console.log(`Successfully sent message notification to ${receiverId} from ${senderName}`);

            // 5. Update unread count for receiver (optional but good for Task 7)
            await admin.firestore().collection("users").doc(receiverId)
                .collection("unread_counts").doc(context.params.chatId).set({
                    count: admin.firestore.FieldValue.increment(1),
                    lastMessage: text,
                    timestamp: admin.firestore.FieldValue.serverTimestamp()
                }, { merge: true });

            return null;
        } catch (error) {
            console.error("Error sending push notification:", error);
            return null;
        }
    });

/**
 * ── CLOUD FUNCTION: ON CALL INITIATED ─────────
 * Triggers when a new call document is created.
 * Sends a high-priority push notification for incoming calls.
 */
exports.onCallInitiated = functions.firestore
    .document("signaling_calls/{chatId}")
    .onWrite(async (change, context) => {
        const callData = change.after.exists ? change.after.data() : null;
        
        if (!callData) return null;

        if (change.before.exists) {
            const oldData = change.before.data();
            if (oldData.status === "ringing" || callData.status !== "ringing") {
                return null;
            }
        } else {
            if (callData.status !== "ringing") return null;
        }
        const receiverId = callData.to;
        const senderId = callData.from;
        const isVideo = callData.isVideoCall || false;

        if (callData.status !== "ringing") return null;

        try {
            const userDoc = await admin.firestore().collection("users").doc(receiverId).get();
            const fcmToken = userDoc.data() ? userDoc.data().fcmToken : null;

            if (!fcmToken) return null;

            const senderName = callData.callerName || "User";
            const senderAvatar = callData.callerAvatarUrl || "";

            const payload = {
                data: {
                    type: "call",
                    chatId: context.params.chatId,
                    callerName: senderName,
                    isVideo: isVideo.toString(),
                    callerAvatarUrl: senderAvatar
                },
                android: {
                    priority: "high"
                },
                token: fcmToken
            };

            await admin.messaging().send(payload);
            console.log(`Successfully sent call notification to ${receiverId}`);
            return null;
        } catch (error) {
            console.error("Error sending call notification:", error);
            return null;
        }
    });

/**
 * ── CLOUD FUNCTION: SEND SECURE OTP ──────────
 * Generates and sends TWO OTPs for dual-verification.
 * One via Email, and another via FCM to the old device.
 */
exports.sendSecureOTP = functions.https.onRequest((req, res) => {
    return cors(req, res, async () => {
        if (req.method !== "POST") return res.status(405).send("Method Not Allowed");

        const { userId, email } = req.body;
        if (!userId || !email) {
            return res.status(400).json({ status: "error", message: "userId and email are required" });
        }

        try {
            const emailOTP = Math.floor(100000 + Math.random() * 900000).toString();
            const deviceOTP = Math.floor(100000 + Math.random() * 900000).toString();

            // 1. Store in Firestore
            await admin.firestore().collection("security_checks").doc(userId).set({
                emailOTP,
                deviceOTP,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                status: "pending"
            });

            // 2. Send Email OTP (Async, don't block FCM)
            const user = process.env.EMAIL_USER || functions.config().email?.user;
            const pass = process.env.EMAIL_PASS || functions.config().email?.pass;
            if (user && pass && email && email.includes("@")) {
                const transporter = nodemailer.createTransport({ service: "gmail", auth: { user, pass } });
                transporter.sendMail({
                    from: `"WattsHub Security" <${user}>`,
                    to: email,
                    subject: "WattsHub: Secure Login Attempt",
                    html: `
                        <div style="font-family: sans-serif; padding: 20px; border: 1px solid #00E676; border-radius: 12px;">
                            <h2 style="color: #00E676;">New Device Verification</h2>
                            <p>A new device is attempting to log into your WattsHub account.</p>
                            <p>Enter this code in the <b>Email Verification</b> field:</p>
                            <h1 style="letter-spacing: 5px;">${emailOTP}</h1>
                            <p style="color: #888;">If this wasn't you, please ignore this and secure your account.</p>
                        </div>
                    `
                }).catch(err => console.error("❌ Email Delivery Error:", err));
            } else {
                console.warn(`⚠️ [OTP] Skipping email delivery for ${email} (Config missing or invalid address)`);
            }

            // 3. Send Device OTP via FCM
            const userDoc = await admin.firestore().collection("users").doc(userId).get();
            const fcmToken = userDoc.exists ? userDoc.data().fcmToken : null;
            if (fcmToken) {
                const payload = {
                    data: {
                        type: "security_otp",
                        otp: deviceOTP,
                        title: "Security Verification Code",
                        body: "New device login attempt. Your code is: " + deviceOTP
                    },
                    token: fcmToken
                };
                try {
                    await admin.messaging().send(payload);
                    console.log(`✅ [OTP] FCM delivered to user ${userId}`);
                } catch (err) {
                    console.error("❌ FCM Delivery Error:", err);
                }
            } else {
                console.warn(`⚠️ [OTP] No FCM token found for user ${userId}. OTP only available via Firestore listener.`);
            }

            return res.status(200).json({ status: "success", message: "Dual-OTP initiated" });
        } catch (error) {
            console.error("❌ Dual-OTP Fatal Error:", error);
            return res.status(500).json({ status: "error", message: error.message });
        }
    });
});


/**
 * ── CLOUD FUNCTION: VERIFY AUTHENTICATOR ─────
 * Verifies a TOTP token against a stored secret.
 */
exports.verifyAuthenticatorToken = functions.https.onRequest((req, res) => {
    return cors(req, res, async () => {
        if (req.method !== "POST") return res.status(405).send("Method Not Allowed");

        const { userId, token, secret } = req.body;
        if (!token) return res.status(400).json({ status: "error", message: "token is required" });

        try {
            let activeSecret = secret;
            if (!activeSecret && userId) {
                const userDoc = await admin.firestore().collection("users").doc(userId).get();
                activeSecret = userDoc.data()?.authenticatorSecret;
            }

            if (!activeSecret) {
                return res.status(400).json({ status: "error", message: "No secret found for user" });
            }

            const isValid = checkTOTP(token, activeSecret);
            return res.status(200).json({ 
                status: isValid ? "success" : "error", 
                isValid 
            });
        } catch (error) {
            console.error("❌ TOTP Verification Error:", error);
            return res.status(500).json({ status: "error", message: error.message });
        }
    });
});

/**
 * ── TOTP ENGINE (Library-free Implementation) ──
 */
function checkTOTP(token, secretBase32) {
    const secret = decodeBase32(secretBase32);
    const timeStep = Math.floor(Date.now() / 30000);
    
    // Window of 1 offset for clock drift
    for (let i = -1; i <= 1; i++) {
        if (generateTOTP(secret, timeStep + i) === token) return true;
    }
    return false;
}

function decodeBase32(str) {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    let bits = '';
    for (let i = 0; i < str.length; i++) {
        const val = alphabet.indexOf(str[i].toUpperCase());
        if (val === -1) continue;
        bits += val.toString(2).padStart(5, '0');
    }
    const bytes = [];
    for (let i = 0; i + 8 <= bits.length; i += 8) {
        bytes.push(parseInt(bits.substring(i, i+8), 2));
    }
    return Buffer.from(bytes);
}

function generateTOTP(secret, step) {
    const buf = Buffer.alloc(8);
    let tmpStep = BigInt(step);
    for (let i = 7; i >= 0; i--) {
        buf[i] = Number(tmpStep & 0xffn);
        tmpStep >>= 8n;
    }
    const hmac = require('crypto').createHmac('sha1', secret);
    hmac.update(buf);
    const hash = hmac.digest();
    const offset = hash[hash.length - 1] & 0xf;
    const binary = ((hash[offset] & 0x7f) << 24) |
                   ((hash[offset + 1] & 0xff) << 16) |
                   ((hash[offset + 2] & 0xff) << 8) |
                   (hash[offset + 3] & 0xff);
    return (binary % 1000000).toString().padStart(6, '0');
}

/**
 * ── CLOUD FUNCTION: ON LOGIN ATTEMPT ──────────
 * Triggers when a new login attempt is detected.
 * Sends a high-priority push notification to the old device.
 */
exports.onLoginAttempt = functions.firestore
    .document("users/{userId}")
    .onUpdate(async (change, context) => {
        const newData = change.after.data();
        const oldData = change.before.data();
        
        const loginAttempt = newData.loginAttempt;
        const oldAttempt = oldData.loginAttempt;
        
        // Only trigger if a NEW pending attempt is created.
        // Guard: if no loginAttempt field changed at all, skip entirely.
        if (!loginAttempt || loginAttempt.status !== "pending") return null;
        // Fix: Firestore Timestamp objects are not reference-equal with ===.
        // Compare .seconds to detect whether this is truly a fresh attempt.
        const newSecs = loginAttempt.timestamp ? loginAttempt.timestamp.seconds : null;
        const oldSecs = (oldAttempt && oldAttempt.timestamp) ? oldAttempt.timestamp.seconds : null;
        if (oldAttempt && oldAttempt.status === "pending" && newSecs === oldSecs) return null;

        const fcmToken = newData.fcmToken;
        if (!fcmToken) {
            console.log(`No FCM token for user ${context.params.userId}. Waiting for Firestore sync.`);
            return null;
        }

        const deviceName = loginAttempt.deviceName || "Unknown Device";

        try {
            const payload = {
                data: {
                    type: "login_alert",
                    title: "Security Alert",
                    body: `A device named '${deviceName}' is trying to log in. Tap to respond.`,
                    deviceName: deviceName
                },
                android: {
                    priority: "high"
                },
                token: fcmToken
            };

            await admin.messaging().send(payload);
            console.log(`Successfully sent login alert to ${context.params.userId}`);
            return null;
        } catch (error) {
            console.error("Error sending login alert notification:", error);
            return null;
        }
    });

/**
 * ── CLOUD FUNCTION: CLEANUP OLD MEDIA ──────────
 * Runs every 24 hours to delete media older than 30 days.
 * Ensures storage efficiency by removing old photos, videos, and voice notes.
 */
exports.cleanupOldMedia = functions.pubsub.schedule("every 24 hours").onRun(async (context) => {
    const bucket = admin.storage().bucket();
    const [files] = await bucket.getFiles({ prefix: "chat_media/" });
    
    const now = Date.now();
    const expirationMs = 30 * 24 * 60 * 60 * 1000; // 30 Days in Milliseconds
    
    let deletedCount = 0;
    const deletionPromises = [];

    for (const file of files) {
        try {
            const [metadata] = await file.getMetadata();
            const createdOn = new Date(metadata.timeCreated).getTime();
            
            if (now - createdOn > expirationMs) {
                deletionPromises.push(file.delete());
                deletedCount++;
            }
        } catch (err) {
            console.error(`Error processing file ${file.name}:`, err);
        }
    }
    
    await Promise.all(deletionPromises);
    console.log(`✅ [CLEANUP] Finished daily storage maintenance. Deleted ${deletedCount} files older than 30 days.`);
    return null;
});
