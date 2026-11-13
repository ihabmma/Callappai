package com.example.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.LearningInsight
import com.example.domain.model.LiveCallState
import com.example.domain.model.PermissionSummary
import com.example.domain.model.PhoneActionPayload
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ResponderSettings
import com.example.domain.model.ScheduledReminder
import com.example.domain.model.StorageStats
import com.example.domain.model.TrainedQaRule
import com.example.service.CallResponderService
import com.example.service.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val replySample: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        ServiceLocator.init(application)
    }

    private val settingsStore = ServiceLocator.settingsDataStore
    private val callLogRepository = ServiceLocator.callLogRepository
    private val storageRetentionManager = ServiceLocator.storageRetentionManager
    private val aiRepository = ServiceLocator.aiResponderRepository
    private val sessionManager = ServiceLocator.callSessionManager
    private val ttsEngine = ServiceLocator.ttsAudioEngine
    private val phoneActionExecutor = ServiceLocator.phoneActionExecutor

    val settings: StateFlow<ResponderSettings> = settingsStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ResponderSettings()
        )

    val liveCallState: StateFlow<LiveCallState> = sessionManager.liveState

    val callLogs: StateFlow<List<CallLogItem>> = callLogRepository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val callerProfiles: StateFlow<List<CallerProfile>> = callLogRepository.allProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val learningInsights: StateFlow<List<LearningInsight>> = callLogRepository.allLearningInsights
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val trainedRules: StateFlow<List<TrainedQaRule>> = callLogRepository.allTrainedRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val scheduledReminders: StateFlow<List<ScheduledReminder>> = callLogRepository.allScheduledReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val storageStats: StateFlow<StorageStats> = storageRetentionManager.storageStatsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StorageStats()
        )

    private val _permissionSummary = MutableStateFlow(computePermissionSummary())
    val permissionSummary: StateFlow<PermissionSummary> = _permissionSummary.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    init {
        viewModelScope.launch {
            storageRetentionManager.enforceRetentionPolicy()
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { current ->
                sessionManager.onServiceStateChanged(current.isServiceEnabled)
            }
        }
    }

    fun refreshPermissions() {
        _permissionSummary.value = computePermissionSummary()
    }

    private fun computePermissionSummary(): PermissionSummary {
        val ctx = getApplication<Application>()
        fun has(perm: String): Boolean =
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

        val answerPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            has(Manifest.permission.ANSWER_PHONE_CALLS)
        } else {
            true
        }
        val notifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else {
            true
        }
        val ignoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = ctx.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        } else {
            true
        }

        return PermissionSummary(
            readPhoneState = has(Manifest.permission.READ_PHONE_STATE),
            recordAudio = has(Manifest.permission.RECORD_AUDIO),
            answerPhoneCalls = answerPerm,
            readCallLog = has(Manifest.permission.READ_CALL_LOG),
            readContacts = has(Manifest.permission.READ_CONTACTS),
            postNotifications = notifPerm,
            canDrawOverlays = canDrawOverlays,
            ignoringBatteryOptimizations = ignoringBattery
        )
    }

    fun requiredPermissionsList(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            list.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val ctx = getApplication<Application>()
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open overlay permission settings", e)
            _uiNotice.value = "يرجى تفعيل صلاحية الظهور فوق التطبيقات من إعدادات الجهاز."
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val ctx = getApplication<Application>()
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open battery optimization dialog", e)
            _uiNotice.value = "يمكنك استثناء التطبيق من قيود البطارية عبر إعدادات الجهاز."
        }
    }

    fun toggleResponderService(enable: Boolean) {
        viewModelScope.launch {
            refreshPermissions()
            val perms = _permissionSummary.value
            if (enable && !perms.coreGranted) {
                _uiNotice.value = "يرجى منح صلاحيات الهاتف والميكروفون أولاً لتفعيل المجيب الآلي."
                return@launch
            }
            try {
                settingsStore.setServiceEnabled(enable)
                val ctx = getApplication<Application>()
                if (enable) {
                    CallResponderService.startService(ctx)
                    _uiNotice.value = "تم تفعيل فريق المجيب الذكي في الخلفية."
                } else {
                    CallResponderService.stopService(ctx)
                    sessionManager.endActiveSession()
                    _uiNotice.value = "تم إيقاف خدمة المجيب الآلي."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed toggling foreground service", e)
                settingsStore.setServiceEnabled(false)
                _uiNotice.value = "تعذر تشغيل الخدمة الخلفية: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun startLiveTestSession() {
        refreshPermissions()
        val hasMic = _permissionSummary.value.recordAudio
        sessionManager.startActiveSession(
            callerNumber = "جلسة اختبار مباشر",
            isTestSession = true,
            enableContinuousMicLoop = hasMic
        )
    }

    fun stopLiveTestSession() {
        sessionManager.endActiveSession()
    }

    fun sendSimulatedCallerUtterance(text: String) {
        sessionManager.submitCallerTextTurn(text)
    }

    fun saveSettings(updated: ResponderSettings) {
        viewModelScope.launch {
            try {
                settingsStore.updateSettings(updated)
                storageRetentionManager.enforceRetentionPolicy()
                _uiNotice.value = "تم حفظ الإعدادات وتطبيق سياسة المساحة والطاقة بنجاح."
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving settings", e)
                _uiNotice.value = "تعذر حفظ الإعدادات: ${e.localizedMessage}"
            }
        }
    }

    fun runStorageCleanupNow() {
        viewModelScope.launch {
            val removed = storageRetentionManager.enforceRetentionPolicy()
            _uiNotice.value = if (removed > 0) {
                "تم تنظيف $removed سجل مكالمات قديم لتوفير المساحة."
            } else {
                "المساحة منظفة بالفعل ولا توجد سجلات متجاوزة للمدة."
            }
        }
    }

    fun saveCallerProfile(
        phoneNumber: String,
        displayName: String,
        category: CallerCategory,
        notes: String,
        isDeflectMode: Boolean
    ) {
        val cleanPhone = phoneNumber.trim()
        if (cleanPhone.isBlank()) {
            _uiNotice.value = "يرجى إدخال رقم الهاتف أولاً."
            return
        }
        viewModelScope.launch {
            val existing = callLogRepository.getCallerProfile(cleanPhone)
            callLogRepository.upsertCallerProfile(
                CallerProfile(
                    phoneNumber = cleanPhone,
                    displayName = displayName.trim(),
                    category = category,
                    notes = notes.trim(),
                    callCount = existing?.callCount ?: 0,
                    lastCallMillis = existing?.lastCallMillis ?: System.currentTimeMillis(),
                    isDeflectMode = isDeflectMode
                )
            )
            _uiNotice.value = "تم حفظ تصنيف المتصل في الذاكرة."
        }
    }

    fun deleteCallerProfile(phoneNumber: String) {
        viewModelScope.launch {
            callLogRepository.deleteCallerProfile(phoneNumber)
        }
    }

    fun addManualInsight(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            callLogRepository.addLearningInsight("قاعدة مخصصة", clean)
            _uiNotice.value = "تمت إضافة قاعدة التطوير الذاتي لفريق المجيب."
        }
    }

    fun deleteInsight(id: Long) {
        viewModelScope.launch {
            callLogRepository.deleteInsight(id)
        }
    }

    fun clearAllInsights() {
        viewModelScope.launch {
            callLogRepository.clearInsights()
            _uiNotice.value = "تم مسح سجل التطوير الذاتي."
        }
    }

    fun addTrainedQaRule(
        keywords: String,
        replyText: String,
        category: CallerCategory,
        strategy: HandlingStrategy,
        shouldEndCall: Boolean
    ) {
        val cleanKw = keywords.trim()
        val cleanReply = replyText.trim()
        if (cleanKw.isBlank() || cleanReply.isBlank()) {
            _uiNotice.value = "يرجى كتابة الكلمات المفتاحية والرد المعتمد للتدريب."
            return
        }
        viewModelScope.launch {
            callLogRepository.saveTrainedRule(
                TrainedQaRule(
                    triggerKeywords = cleanKw,
                    replyText = cleanReply,
                    category = category,
                    strategy = strategy,
                    shouldEndCall = shouldEndCall
                )
            )
            _uiNotice.value = "تم تدريب المحرك المحلي (أوفلاين) على الرد الجديد."
        }
    }

    fun deleteTrainedRule(id: Long) {
        viewModelScope.launch {
            callLogRepository.deleteTrainedRule(id)
        }
    }

    fun createPhoneAction(
        type: PhoneActionType,
        title: String,
        hour: Int? = null,
        minute: Int? = null,
        delayMinutes: Int? = null
    ) {
        viewModelScope.launch {
            val result = phoneActionExecutor.execute(
                payload = PhoneActionPayload(
                    type = type,
                    title = title,
                    hour = hour,
                    minute = minute,
                    delayMinutes = delayMinutes
                ),
                sourceCaller = "إدخال يدوي"
            )
            if (result.executed) {
                _uiNotice.value = result.arabicSummary
            }
        }
    }

    fun deleteScheduledReminder(id: Long) {
        viewModelScope.launch {
            phoneActionExecutor.cancelLocalSchedule(id)
            callLogRepository.deleteScheduledReminder(id)
            _uiNotice.value = "تم إلغاء وحذف التذكير."
        }
    }

    fun testAiConnection(draftSettings: ResponderSettings) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            try {
                val result = aiRepository.testConnection(draftSettings)
                result.fold(
                    onSuccess = { reply ->
                        _connectionTestState.value = ConnectionTestState.Success(reply)
                    },
                    onFailure = { error ->
                        _connectionTestState.value = ConnectionTestState.Error(
                            error.localizedMessage ?: "فشل اختبار الاتصال."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState.Error(
                    e.localizedMessage ?: "حدث خطأ أثناء الاختبار."
                )
            }
        }
    }

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    fun testTtsVoice(draftSettings: ResponderSettings) {
        viewModelScope.launch {
            val ok = ttsEngine.speakAndAwait(
                text = draftSettings.greetingMessage,
                speechRate = draftSettings.ttsSpeechRate,
                pitch = draftSettings.ttsPitch
            )
            if (!ok) {
                _uiNotice.value = "تعذر تشغيل محرك النطق العربي على هذا الجهاز."
            }
        }
    }

    fun deleteCallLog(id: Long) {
        viewModelScope.launch {
            callLogRepository.deleteCallLog(id)
        }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch {
            callLogRepository.clearAllLogs()
            _uiNotice.value = "تم مسح سجل المكالمات."
        }
    }

    fun clearNotice() {
        _uiNotice.value = null
    }
}
