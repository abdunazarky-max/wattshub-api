const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const admin = require('firebase-admin');
const cors = require('cors');

// 1. Initialize Firebase Admin
const serviceAccount = require("./serviceAccountKey.json");

// Robustly fix private key formatting
if (serviceAccount.private_key) {
  serviceAccount.private_key = serviceAccount.private_key.replace(/\\n/g, '\n');
}

try {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
  console.log("[Firebase] Admin SDK initialized successfully");
} catch (error) {
  console.error("[Firebase] Initialization error:", error.message);
}

const db = admin.firestore();
const messaging = admin.messaging();

const app = express();
app.use(cors());
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

// Store active socket connections: userId -> socketId
const activeUsers = new Map();

io.on('connection', (socket) => {
  console.log('A user connected:', socket.id);

  // 2. Handle Device Registration
  socket.on('register-device', (data) => {
    const { userId, deviceName } = data;
    if (userId) {
      socket.join(userId); // Join user-specific room for multi-device delivery
      console.log(`User ${userId} joined room from ${deviceName || 'unknown device'}`);
      
      // Update online status in Firestore
      try {
        db.collection('users').doc(userId).update({
          status: 'online',
          lastSeen: Date.now()
        }).then(() => {
          console.log(`[Presence] User ${userId} is now online`);
        }).catch(err => {
          console.error(`[Presence Error] Could not update Firestore for ${userId}:`, err.message);
          // Don't throw, let the socket connection continue
        });
      } catch (e) {
        console.error("[Presence Exception] Local error updating status:", e.message);
      }
    }
  });

  // 3. Handle Real-Time Message Sending
  socket.on('send-message', async (data) => {
    const { senderId, receiverId, text, chatId, isGroup, senderName, senderPic, clientHandled } = data;
    
    console.log(`Message from ${senderId} to ${receiverId}: ${text} (ClientHandled: ${!!clientHandled})`);

    // Prepare message for relay
    const relayData = {
      senderId, text, chatId, senderName, 
      senderPic: senderPic || '',
      isGroup: isGroup || false,
      timestamp: Date.now()
    };

    // 🚀 STEP 1: REAL-TIME RELAY
    const receiverRoom = io.sockets.adapter.rooms.get(receiverId);
    const isOnline = receiverRoom && receiverRoom.size > 0;
    
    if (isOnline) {
      io.to(receiverId).emit('receive-message', relayData);
      console.log(`Relay via socket to ${receiverId}`);
    }

    // 🚀 STEP 2: PERSISTENCE (In Background)
    try {
      if (!clientHandled) {
        const messageData = {
          sender_id: senderId,
          receiver_id: receiverId,
          text: text,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          type: 'text',
          status: 'sent',
          sender_name: senderName || 'User',
          sender_pic: senderPic || ''
        };

        const collectionPath = isGroup ? `groups/${chatId}/messages` : `messages/${chatId}/contents`;
        
        Promise.all([
          db.collection(collectionPath).add(messageData),
          db.collection('conversations').doc(chatId).set({
            lastMessage: text,
            lastMessageTimestamp: admin.firestore.FieldValue.serverTimestamp(),
            participantIds: isGroup ? [] : [senderId, receiverId]
          }, { merge: true }),
          // Also update unread count for offline delivery guarantee
          db.collection('users').doc(receiverId)
            .collection('unread_counts').doc(chatId).set({
              count: admin.firestore.FieldValue.increment(1),
              lastMessage: text,
              timestamp: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true })
        ]).catch(e => console.error("Persistence failed:", e));
      }

      // 🚀 STEP 3: FCM FOR RELIABILITY
      // We always send FCM for chat messages because background sockets are unreliable on Android
      console.log(`Sending FCM to ${receiverId} for reliability...`);
      
      const userDoc = await db.collection('users').doc(receiverId).get();
      const fcmToken = userDoc.data()?.fcmToken;

      if (fcmToken) {
        const payload = {
          token: fcmToken,
          notification: {
            title: senderName || 'New Message',
            body: text,
          },
          data: {
            type: 'chat',
            senderId: senderId,
            senderName: senderName || 'User',
            message: text,
            body: text,
            chatId: chatId,
            avatarUrl: senderPic || '',
            isGroup: isGroup ? 'true' : 'false',
            navigate_to: `chat/${senderId}/${senderName || 'User'}/${isGroup ? 'true' : 'false'}`
          },
          android: {
            priority: 'high',
            notification: {
              channelId: 'chat_messages_v6',
              sound: 'default',
              clickAction: 'FLUTTER_NOTIFICATION_CLICK' // Common legacy flag to ensure onMessageReceived is triggered
            }
          }
        };
        messaging.send(payload).catch(e => console.error("FCM Send failed:", e));
      }
    } catch (err) {
      console.error("Error processing message routing:", err);
    }
  });

  // 📞 WebRTC Signaling & Real-Time Calling Relay
  socket.on('video-offer', async (data) => {
    const { to, from, offer, isVideoCall, callerName, callerAvatarUrl, callLogId } = data;
    console.log(`[Signaling] 📞 Offer from ${from} to ${to} (Video: ${isVideoCall})`);

    const logId = callLogId || `${from}_${to}_${Date.now()}`;
    data.callLogId = logId; // ✅ Attach logId so receiver can sync history status
    const logData = {
      chatId: from < to ? `${from}_${to}` : `${to}_${from}`,
      senderId: from,
      receiverId: to,
      callerName: callerName || "User",
      callerPicUrl: callerAvatarUrl || "",
      isVideoCall: !!isVideoCall,
      status: 'outgoing', // Initial status
      participantIds: [from, to],
      timestamp: admin.firestore.FieldValue.serverTimestamp()
    };
    
    db.collection('call_logs').doc(logId).set(logData).catch(err => console.error("❌ Firestore Log Error:", err));
    
    io.to(to).emit('video-offer', data);

    // 3. Trigger High-Priority FCM for Calling (to wake up the phone)
    try {
      const userDoc = await db.collection('users').doc(to).get();
      const fcmToken = userDoc.data()?.fcmToken;
      if (fcmToken) {
        const payload = {
          token: fcmToken,
          data: {
            type: 'call',
            chatId: from < to ? `${from}_${to}` : `${to}_${from}`, 
            callerId: from,
            callerName: callerName || 'Partner',
            callerAvatarUrl: callerAvatarUrl || '',
            isVideo: isVideoCall ? 'true' : 'false'
          },
          android: {
            priority: 'high',
            ttl: 0, // Expire immediately if unreachable
          }
        };
        messaging.send(payload).catch(e => console.log("⚠️ FCM Send failed:", e.message));
      }
    } catch (e) {
      console.error("[FCM Error]", e.message);
    }
  });

  socket.on('video-answer', (data) => {
    console.log(`[CallRelay] Answer from ${data.from} to ${data.to}`);
    io.to(data.to).emit('video-answer', data);
  });

  socket.on('ice-candidate', (data) => {
    io.to(data.to).emit('ice-candidate', data);
  });

  // Handle Call Lifecycle Events
  socket.on('call-decline', async (data) => {
    console.log(`[CallRelay] Call declined by ${data.from} for ${data.to}`);
    io.to(data.to).emit('call-decline', data);
    
    // Update log to declined
    try {
      const snap = await db.collection('call_logs')
        .where('participantIds', 'array-contains', data.from)
        .orderBy('timestamp', 'desc').limit(1).get();
      if (!snap.empty && snap.docs[0].data().status === 'outgoing') {
        await snap.docs[0].ref.update({ status: 'declined' });
      }
    } catch(e) {}
  });

  socket.on('call-end', async (data) => {
    console.log(`[CallRelay] Call ended by ${data.from}`);
    io.to(data.to).emit('call-ended', data);

    // Update log to missed (if not already completed)
    try {
      const snap = await db.collection('call_logs')
        .where('participantIds', 'array-contains', data.from)
        .orderBy('timestamp', 'desc').limit(1).get();
      if (!snap.empty) {
        const doc = snap.docs[0];
        const logData = doc.data();
        if (logData.status === 'outgoing') {
          const isCallerClosing = logData.senderId === data.from;
          const newStatus = isCallerClosing ? 'missed' : 'completed';
          await doc.ref.update({ status: newStatus });

          // 🔕 Send Missed Call Notification if it was a real missed call
          if (isCallerClosing) {
             const userDoc = await db.collection('users').doc(data.to).get();
             const fcmToken = userDoc.data()?.fcmToken;
             if (fcmToken) {
               messaging.send({
                 token: fcmToken,
                 notification: {
                   title: 'Missed Call',
                   body: `You missed a call from ${logData.callerName || 'User'}`
                 },
                 data: {
                   type: 'missed_call',
                   chatId: logData.chatId
                 }
               }).catch(e => console.log("Missed Call FCM failed"));
             }
          }
        }
      }
    } catch(e) {}
  });

  socket.on('call-busy', (data) => {
    console.log(`[CallRelay] User ${data.from} is busy`);
    io.to(data.to).emit('call-busy', data);
  });

  socket.on('disconnecting', () => {
    // Rooms are still joined in 'disconnecting'
    for (const userId of socket.rooms) {
      if (userId !== socket.id) {
        const room = io.sockets.adapter.rooms.get(userId);
        if (room && room.size === 1) { // This is the last device for this user
          console.log(`User ${userId} last device disconnected`);
          db.collection('users').doc(userId).update({
            status: 'offline',
            lastSeen: Date.now()
          }).catch(err => console.error("Error updating status:", err));
        }
      }
    }
  });
});

const PORT = process.env.PORT || 3005;
server.listen(PORT, () => {
  console.log(`Signaling server running on port ${PORT}`);
});
