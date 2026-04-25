package com.hyzin.whtsappclone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions", color = Color(0xFF00FF9D), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00FF9D)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.8f),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .paint(
                    painter = painterResource(id = R.drawable.login_bg_3d),
                    contentScale = ContentScale.Crop
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "WattsHub Terms and Conditions",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF9D),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Effective Date: April 05, 2026",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                TermsSection("Welcome to WattsHub", "Welcome to WattsHub, a messaging and calling service that allows users to send text messages, images, videos, and make voice and video calls. These Terms and Conditions (“Agreement”) govern your use of WattsHub services (the “Service”). By using WattsHub, you agree to these Terms.")

                TermsSection("1. Use of Service", "Eligibility\n• You must be at least 18 years old to use WattsHub.\n• By using the Service, you confirm you have the legal authority to enter into this Agreement.\n\nPermitted Use\n• You may use WattsHub to communicate with friends, family, and colleagues.\n• You may not use the Service to send spam, phishing messages, or unlawful content.\n• You may not sell, resell, or monetize the Service.\n\nUser Accounts & Security\n• You must provide accurate account information.\n• You are responsible for the security of your account, passwords, and any devices accessing WattsHub.\n• Notify us immediately if you suspect unauthorized use of your account.")

                TermsSection("Prohibited Conduct", "You shall not:\na. Harass, threaten, or defame others.\nb. Distribute illegal, offensive, or harmful content.\nc. Attempt to access other users’ accounts or private messages.\nd. Interfere with the Service or attempt to reverse engineer it.\ne. Use automated tools to collect data or spam users.\n\nSuspension & Termination\n• We may suspend or terminate your access if we reasonably believe you violate these Terms.\n• WattsHub reserves the right to remove or block content that violates these Terms.")

                TermsSection("2. Messaging and Calling Features", "Message and Call Delivery\n• Messages and calls may be delayed or fail due to network issues.\n• WattsHub is not responsible for lost messages or interrupted calls.\n\nEncryption\n• WattsHub may use end-to-end encryption to protect messages and calls.\n• Even with encryption, we cannot guarantee absolute security; you use the Service at your own risk.\n\nUser Content\n• You are responsible for all messages, media, and files you share (“User Content”).\n• By sending User Content, you grant WattsHub a license to transmit, store, and display it as part of the Service.\n• You must have the right to share all User Content you send.")

                TermsSection("3. Feedback, Usage Data, and Privacy", "Feedback\n• Suggestions or feedback are welcome and may be used by WattsHub without obligation or restriction.\n\nUsage Data\n• WattsHub may collect data on usage patterns, device info, and call/message metadata to improve the Service.\n• We may share anonymized or aggregated data with partners or service providers.\n\nPrivacy\n• Our Privacy Policy explains how we collect, store, and use personal information.\n• By using the Service, you consent to data collection as described in our Privacy Policy.")

                TermsSection("4. Intellectual Property", "• WattsHub owns all rights to the Service, Software, and Documentation.\n• No rights are granted except the limited license to use the Service.\n• You may not copy, reverse engineer, or create derivative works from WattsHub.")

                TermsSection("5. Age Restrictions", "• The Service is only available to users 18 years or older.\n• Accounts suspected of being operated by minors will be terminated.")

                TermsSection("6. Liability", "• WattsHub is provided “as is.”\n• We are not liable for indirect, incidental, or consequential damages arising from use of the Service.\n• Messages, calls, or content are transmitted over the Internet and may be delayed, intercepted, or lost.")

                TermsSection("7. Reporting Abuse", "• Users may report abusive or illegal content via the app.\n• WattsHub may review, remove, or block reported content in accordance with these Terms.")

                TermsSection("8. Governing Law", "• This Agreement is governed by the laws of your local jurisdiction.\n• Any disputes shall be resolved in appropriate courts.")

                TermsSection("9. Amendments", "• WattsHub may update these Terms from time to time.\n• Continued use of the Service indicates acceptance of updated Terms.")

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF9D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I UNDERSTAND", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TermsSection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FF9D)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            color = Color.White,
            lineHeight = 20.sp
        )
    }
}
