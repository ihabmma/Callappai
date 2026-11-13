package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiProviderMode
import com.example.domain.model.ResponderSettings
import com.example.ui.viewmodel.ConnectionTestState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    savedSettings: ResponderSettings,
    connectionTestState: ConnectionTestState,
    onSaveSettings: (ResponderSettings) -> Unit,
    onTestConnection: (ResponderSettings) -> Unit,
    onResetTestState: () -> Unit,
    onTestTts: (ResponderSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var aiMode by remember(savedSettings) { mutableStateOf(savedSettings.aiProviderMode) }
    var customApiKey by remember(savedSettings) { mutableStateOf(savedSettings.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var customModelName by remember(savedSettings) { mutableStateOf(savedSettings.customModelName) }

    var greetingMessage by remember(savedSettings) { mutableStateOf(savedSettings.greetingMessage) }
    var systemPersona by remember(savedSettings) { mutableStateOf(savedSettings.systemPersona) }
    var fallbackMessage by remember(savedSettings) { mutableStateOf(savedSettings.fallbackMessage) }

    var autoAnswerEnabled by remember(savedSettings) { mutableStateOf(savedSettings.autoAnswerEnabled) }
    var autoAnswerDelay by remember(savedSettings) { mutableIntStateOf(savedSettings.autoAnswerDelaySeconds) }
    var useSpeakerphone by remember(savedSettings) { mutableStateOf(savedSettings.useSpeakerphoneForReply) }
    var speechRate by remember(savedSettings) { mutableFloatStateOf(savedSettings.ttsSpeechRate) }
    var ttsPitch by remember(savedSettings) { mutableFloatStateOf(savedSettings.ttsPitch) }
    var recognitionLocale by remember(savedSettings) { mutableStateOf(savedSettings.recognitionLocale) }

    LaunchedEffect(aiMode, customApiKey, customModelName) {
        onResetTestState()
    }

    fun buildDraft(): ResponderSettings = savedSettings.copy(
        aiProviderMode = aiMode,
        customApiKey = customApiKey.trim(),
        customModelName = customModelName.trim(),
        greetingMessage = greetingMessage.trim(),
        systemPersona = systemPersona.trim(),
        fallbackMessage = fallbackMessage.trim(),
        autoAnswerEnabled = autoAnswerEnabled,
        autoAnswerDelaySeconds = autoAnswerDelay,
        useSpeakerphoneForReply = useSpeakerphone,
        ttsSpeechRate = speechRate,
        ttsPitch = ttsPitch,
        recognitionLocale = recognitionLocale
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "إعدادات المجيب الذكي",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Section 1: AI Engine & Google AI Studio Key
            item {
                SettingsSectionCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "محرك الذكاء الاصطناعي (Gemini)"
                ) {
                    Text(
                        text = "اختر طريقة الاتصال بنموذج Gemini:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = aiMode == AiProviderMode.FIREBASE_AI_LOGIC,
                            onClick = { aiMode = AiProviderMode.FIREBASE_AI_LOGIC },
                            label = { Text("الذكاء المدمج (تلقائي)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = aiMode == AiProviderMode.GOOGLE_AI_STUDIO_KEY,
                            onClick = { aiMode = AiProviderMode.GOOGLE_AI_STUDIO_KEY },
                            label = { Text("مفتاح AI Studio") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedVisibility(visible = aiMode == AiProviderMode.GOOGLE_AI_STUDIO_KEY) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = customApiKey,
                                onValueChange = { customApiKey = it },
                                label = { Text("مفتاح Google AI Studio API Key") },
                                placeholder = { Text("AIzaSy...") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "إظهار أو إخفاء المفتاح"
                                        )
                                    }
                                },
                                visualTransformation = if (showApiKey) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input")
                            )

                            Text(
                                text = "النموذج المستخدم:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash").forEach { model ->
                                    FilterChip(
                                        selected = customModelName == model,
                                        onClick = { customModelName = model },
                                        label = { Text(model, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onTestConnection(buildDraft()) },
                        enabled = connectionTestState !is ConnectionTestState.Testing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_connection_button")
                    ) {
                        if (connectionTestState is ConnectionTestState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري اختبار الاتصال...")
                        } else {
                            Text("اختبار الاتصال بمحرك الذكاء الاصطناعي")
                        }
                    }

                    when (connectionTestState) {
                        is ConnectionTestState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "تم الاتصال بنجاح!",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = connectionTestState.replySample,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        is ConnectionTestState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = connectionTestState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Section 2: Greeting, Persona, Fallback
            item {
                SettingsSectionCard(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "رسائل الترحيب وشخصية المجيب"
                ) {
                    Text(
                        text = "قوالب شخصية سريعة:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                greetingMessage = "مرحباً بك، أنا المساعد الشخصي الذكي. صاحب الهاتف مشغول حالياً، كيف أساعدك؟"
                                systemPersona = "أنت سكرتير شخصي مهذب تتحدث العربية الفصحى المبسطة. استعلم عن اسم المتصل وسبب الاتصال ولخّصه بلطف."
                            },
                            label = { Text("سكرتير شخصي") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                greetingMessage = "أهلاً بك في مكتب خدمة العملاء الذكي. تفضل بطرح استفسارك."
                                systemPersona = "أنت موظف استقبال أعمال محترف ولطيف. أجب باحترافية واطلب تفاصيل الطلب ورقم التواصل إن لزم."
                            },
                            label = { Text("استقبال أعمال") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                greetingMessage = "أهلاً بك، صاحب الهاتف يقود السيارة حالياً. أخبرني بأمرك وسأوصله له فوراً."
                                systemPersona = "أنت مساعد صوتي سريع وواضح تساعد المتصلين أثناء قيادة صاحب الهاتف. اطلب الرسالة المهمة بإيجاز."
                            },
                            label = { Text("أثناء القيادة") }
                        )
                    }

                    OutlinedTextField(
                        value = greetingMessage,
                        onValueChange = { greetingMessage = it },
                        label = { Text("رسالة الترحيب التلقائية عند فتح الخط") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("greeting_input")
                    )

                    OutlinedTextField(
                        value = systemPersona,
                        onValueChange = { systemPersona = it },
                        label = { Text("توجيهات وشخصية الذكاء الاصطناعي") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("persona_input")
                    )

                    OutlinedTextField(
                        value = fallbackMessage,
                        onValueChange = { fallbackMessage = it },
                        label = { Text("الرد الاحتياطي عند انقطاع الشبكة") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fallback_input")
                    )
                }
            }

            // Section 3: Call Handling & TTS Audio
            item {
                SettingsSectionCard(
                    icon = Icons.Default.PhoneCallback,
                    title = "المكالمات وإعدادات النطق (TTS)"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الرد التلقائي على المكالمات الواردة",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "فتح المكالمة تلقائياً بعد انتهاء مهلة الرنين",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoAnswerEnabled,
                            onCheckedChange = { autoAnswerEnabled = it }
                        )
                    }

                    AnimatedVisibility(visible = autoAnswerEnabled) {
                        Column {
                            Text(
                                text = "مهلة الرنين قبل الرد التلقائي: $autoAnswerDelay ثوانٍ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = autoAnswerDelay.toFloat(),
                                onValueChange = { autoAnswerDelay = it.roundToInt().coerceIn(1, 10) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تفعيل مكبر الصوت أثناء الرد",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "يساعد على سماع المتصل للرد الصوتي بوضوح",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useSpeakerphone,
                            onCheckedChange = { useSpeakerphone = it }
                        )
                    }

                    Text(
                        text = "لهجة التعرف الصوتي على المتصل:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ar-SA" to "السعودية",
                            "ar-EG" to "مصر",
                            "ar-AE" to "الإمارات",
                            "ar-JO" to "الأردن"
                        ).forEach { (code, title) ->
                            FilterChip(
                                selected = recognitionLocale == code,
                                onClick = { recognitionLocale = code },
                                label = { Text(title) }
                            )
                        }
                    }

                    Text(
                        text = "سرعة النطق: ${String.format(java.util.Locale.US, "%.1fx", speechRate)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = { speechRate = it },
                        valueRange = 0.7f..1.4f
                    )

                    Text(
                        text = "نبرة الصوت (Pitch): ${String.format(java.util.Locale.US, "%.1f", ttsPitch)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = ttsPitch,
                        onValueChange = { ttsPitch = it },
                        valueRange = 0.8f..1.3f
                    )

                    OutlinedButton(
                        onClick = { onTestTts(buildDraft()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_tts_button")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("سماع تجربة صوت الترحيب")
                    }
                }
            }

            // Save Settings Button
            item {
                Button(
                    onClick = { onSaveSettings(buildDraft()) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حفظ الإعدادات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}
