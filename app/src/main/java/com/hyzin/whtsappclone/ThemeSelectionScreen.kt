package com.hyzin.whtsappclone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyzin.whtsappclone.ui.theme.AppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionScreen(
    onBack: () -> Unit,
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    val themes = listOf(
        "STARRY_NIGHT" to "Starry Night",
        "OCEAN_BLUE" to "Ocean Blue",
        "LIGHT" to "Light"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose theme", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            themes.forEach { (id, label) ->
                ThemeOption(
                    label = label,
                    isSelected = currentTheme == id,
                    onClick = {
                        onThemeSelected(id)
                    }
                )
            }
        }
    }
}

@Composable
fun ThemeOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppGreen,
                unselectedColor = Color.Gray
            )
        )
    }
}
