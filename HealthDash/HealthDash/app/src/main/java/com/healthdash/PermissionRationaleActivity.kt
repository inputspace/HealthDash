package com.healthdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PermissionRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Why HealthDash needs access", fontSize = 20.sp)
                            Text("HealthDash reads your steps, distance, calories, heart rate and exercise sessions from Health Connect to display your daily activity dashboard. Your data never leaves your phone.", fontSize = 14.sp)
                            Button(onClick = { finish() }) {
                                Text("OK, got it")
                            }
                        }
                    }
                }
            }
        }
    }
}
