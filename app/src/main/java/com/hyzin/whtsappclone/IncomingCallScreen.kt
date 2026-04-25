package com.hyzin.whtsappclone

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hyzin.whtsappclone.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerAvatarUrl: String = "",
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Pulsing animation for the avatar ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Pulsing Avatar Ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .border(3.dp, Color(0xFF00A884).copy(alpha = 0.35f), CircleShape)
                        .background(Color.Transparent)
                )
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884)),
                    contentAlignment = Alignment.Center
                ) {
                    if (callerAvatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = callerAvatarUrl,
                            contentDescription = "Caller Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = callerName.firstOrNull()?.toString()?.uppercase() ?: "?",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = callerName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isVideo) "Incoming video call..." else "Incoming audio call...",
                fontSize = 16.sp,
                color = Color(0xFF8B949E)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 64.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = onDecline,
                            modifier = Modifier.size(74.dp),
                            shape = CircleShape,
                            color = Color(0xFFFF3B30),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Decline", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // Accept
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = onAccept,
                            modifier = Modifier.size(74.dp),
                            shape = CircleShape,
                            color = AppGreen,
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Accept", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Quick Message Reply
                TextButton(
                    onClick = { /* Handle Quick Reply Toast for now */ },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reply with message", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
    }
}
