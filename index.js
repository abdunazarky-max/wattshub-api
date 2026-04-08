
const express = require('express');
const cors = require('cors');
const http = require('http');
const { Server } = require('socket.io');
const nodemailer = require('nodemailer');
const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*", 
        methods: ["GET", "POST"]
    }
});

const PORT = process.env.PORT || 3002;

app.use(cors());
app.use(express.json());

// ── Firebase Admin SDK Initialization ─────────────────
const possiblePaths = [
    path.join(__dirname, 'serviceAccountKey.json'),
    path.join(__dirname, 'serviceAccountKey.json.json')
];
const serviceAccountPath = possiblePaths.find(p => fs.existsSync(p));
let firestoreDb = null;

if (serviceAccountPath) {
    try {
        admin.initializeApp({
            credential: admin.credential.cert(require(serviceAccountPath))
        });
        firestoreDb = admin.firestore();
        console.log("🔥 Firebase Admin initialized successfully");
    } catch (error) {
        console.error("❌ Firebase initialization failed:", error.message);
    }
} else {
    console.warn("⚠️ Firebase serviceAccountKey.json NOT found!");
}

// Transporter for Email OTPs
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASS
    }
});

// Map userId -> Map(deviceId -> socketId)
const userDevices = new Map();

io.on('connection', (socket) => {
    console.log(`🔌 New client: ${socket.id}`);

    // Register User with Device info
    socket.on('register-device', ({ userId, deviceId, deviceName }) => {
        if (!userId || !deviceId) return;
        
        socket.userId = userId;
        socket.deviceId = deviceId;
        
        if (!userDevices.has(userId)) {
            userDevices.set(userId, new Map());
        }
        userDevices.get(userId).set(deviceId, socket.id);
        
        console.log(`📱 Device Registered: ${userId} (${deviceName})`);
        
        // Update device status in Firestore
        if (firestoreDb) {
            firestoreDb.collection('users').document(userId)
                .collection('devices').document(deviceId).set({
                    name: deviceName || "Unknown Android Device",
                    lastActive: admin.firestore.FieldValue.serverTimestamp(),
                    status: 'online',
                    socketId: socket.id
                }, { merge: true });
        }
    });

    // Chat Logic (Remains unchanged)
    socket.on('send-chat', (data) => {
        const { to, message } = data;
        const targetDevices = userDevices.get(to);
        if (targetDevices) {
            targetDevices.forEach(socketId => {
                io.to(socketId).emit('receive-chat', { 
                    from: socket.userId, 
                    message 
                });
            });
        }
    });

    socket.on('disconnect', () => {
        if (socket.userId && socket.deviceId) {
            const devices = userDevices.get(socket.userId);
            if (devices) {
                devices.delete(socket.deviceId);
                if (devices.size === 0) userDevices.delete(socket.userId);
            }
            // Update Firestore status
            if (firestoreDb) {
                firestoreDb.collection('users').document(socket.userId)
                    .collection('devices').document(socket.deviceId).update({
                        status: 'offline',
                        lastActive: admin.firestore.FieldValue.serverTimestamp()
                    }).catch(() => {});
            }
        }
    });
});

// ── REQUIREMENT #6: OTP DELIVERY & RATE LIMITING ────────────────────────
const otpStore = new Map(); // Simple in-memory fallback, shared with Firestore

app.post('/send-otp', async (req, res) => {
    const { to, type, userId, otp: clientOtp } = req.body; // 'to' is email or mobile
    const otp = clientOtp || Math.floor(100000 + Math.random() * 900000).toString(); // Pass through Android OTP
    
    if (!to) return res.status(400).json({ status: "error", message: "Recipient required" });

    try {
        console.log(`📩 [${type}] Requesting OTP for ${to}`);

        // Rate limiting (simple Simulation)
        const lastSent = otpStore.get(to)?.timestamp;
        if (lastSent && Date.now() - lastSent < 30000) { // 30s resend limit
            return res.status(429).json({ status: "error", message: "Please wait before resending code." });
        }

        otpStore.set(to, { otp, timestamp: Date.now() });

        // Requirement #3: Adv. Protection - Notify other devices if userId is provided (login attempt)
        if (userId && firestoreDb) {
            const activeDevices = await firestoreDb.collection('users').doc(userId).collection('devices').where('status', '==', 'online').get();
            if (!activeDevices.empty) {
                // Notify via socket if possible
                const targetDevices = userDevices.get(userId);
                if (targetDevices) {
                    targetDevices.forEach(sid => {
                        io.to(sid).emit('security-alert', { 
                            type: 'new_login_attempt',
                            otp: otp,
                            time: new Date().toISOString()
                        });
                    });
                }
                // Also update Firestore for persistent alert (MainActivity.kt listens to this)
                await firestoreDb.collection('users').doc(userId).collection('security').doc('login_request').set({
                    otp: otp,
                    timestamp: admin.firestore.FieldValue.serverTimestamp(),
                    status: 'pending'
                });
            }
        }

        if (type === "sms") {
            console.log(`📱 SMS [${to}]: Your WattsHub code is ${otp}`);
            return res.json({ status: "success", strategy: "simulation" });
        } else {
            const mailOptions = {
                from: `"WattsHUB" <${process.env.EMAIL_USER}>`,
                to: to,
                subject: 'WattsHub Verification Code',
                html: `<div style="font-family:sans-serif;background:#0F0F0F;color:#FFF;padding:30px;border-radius:15px;border:1px solid #00FF9D;">
                    <h2 style="color:#00FF9D;">Verification Code</h2>
                    <p>Your OTP code is <strong style="font-size:24px;letter-spacing:5px;">${otp}</strong></p>
                    <p style="color:#888;">Valid for 5 minutes. Do not share it.</p>
                </div>`
            };
            await transporter.sendMail(mailOptions);
            return res.json({ status: "success" });
        }
    } catch (error) {
        console.error('❌ OTP Error:', error);
        res.status(500).json({ status: "error", message: error.message });
    }
});

// ── REQUIREMENT #8: BACKEND OTP VALIDATION ──────────────────────────────
app.post('/validate-otp', (req, res) => {
    const { to, otp } = req.body;
    const storedData = otpStore.get(to);

    if (!storedData) return res.status(404).json({ status: "error", message: "OTP not requested or expired." });
    
    const isExpired = Date.now() - storedData.timestamp > 300000; // 5 minute expiry
    if (isExpired) {
        otpStore.delete(to);
        return res.status(400).json({ status: "error", message: "OTP expired." });
    }

    if (storedData.otp === otp) {
        otpStore.delete(to); // Burn after use
        return res.json({ status: "success", message: "Verified" });
    } else {
        return res.status(400).json({ status: "error", message: "Incorrect verification code." });
    }
});

// ── REQUIREMENT #4: DEVICE MANAGEMENT ───────────────────────────────────
app.get('/devices/:userId', async (req, res) => {
    try {
        const snapshot = await firestoreDb.collection('users').doc(req.params.userId).collection('devices').get();
        const devices = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        res.json({ status: "success", devices });
    } catch (e) {
        res.status(500).json({ status: "error", message: e.message });
    }
});

app.delete('/devices/:userId/:deviceId', async (req, res) => {
    try {
        const { userId, deviceId } = req.params;
        await firestoreDb.collection('users').doc(userId).collection('devices').doc(deviceId).delete();
        
        // Force disconnect if online
        const devices = userDevices.get(userId);
        if (devices && devices.has(deviceId)) {
            const sid = devices.get(deviceId);
            io.to(sid).emit('logout-force', { reason: 'device_removed' });
        }
        
        res.json({ status: "success", message: "Device logged out." });
    } catch (e) {
        res.status(500).json({ status: "error", message: e.message });
    }
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Secure Server running on port ${PORT}`);
});
