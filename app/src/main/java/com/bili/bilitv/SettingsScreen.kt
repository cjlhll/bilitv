package com.bili.bilitv

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE) }
    
    var enableSoftwareDecoder by remember { 
        mutableStateOf(sharedPref.getBoolean("enable_software_decoder", false)) 
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "应用设置",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用软件视频解码",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "解决部分设备播放 HDR/4K 视频时的偏色（发白）或花屏问题。注意：开启后可能会增加 CPU 负载并导致卡顿。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableSoftwareDecoder,
                            onCheckedChange = {
                                enableSoftwareDecoder = it
                                sharedPref.edit().putBoolean("enable_software_decoder", it).apply()
                            }
                        )
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "播放器提示",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当前播放器基于 Android Media3 (ExoPlayer) 构建。HDR 播放效果取决于设备硬件支持情况。如果遇到偏色，请尝试开启上方软件解码开关。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "版本信息: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.6f)
                )
            }
        }
    }
}
