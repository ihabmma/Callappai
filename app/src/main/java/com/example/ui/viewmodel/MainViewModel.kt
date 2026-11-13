package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CallLogItem
import com.example.domain.model.LiveCallState
import com.example.domain.model.PermissionSummary
import com.example.domain.model.ResponderSettings
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
    private val aiRepository = ServiceLocator.aiResponderRepository
    private val sessionManager = ServiceLocator.callSessionManager
    private val ttsEngine = ServiceLocator.ttsAudioEngine

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

    private val _permissionSummary = MutableStateFlow(computePermissionSummary())
    val permissionSummary: StateFlow<PermissionSummary> = _permissionSummary.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    init {
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

        return PermissionSummary(
            readPhoneState = has(Manifest.permission.READ_PHONE_STATE),
            recordAudio = has(Manifest.permission.RECORD_AUDIO),
            answerPhoneCalls = answerPerm,
            readCallLog = has(Manifest.permission.READ_CALL_LOG),
            postNotifications = notifPerm
        )
    }

    fun requiredPermissionsList(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            list.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
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
                    _uiNotice.value = "تم تفعيل خدمة المجيب الآلي في الخلفية."
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
                _uiNotice.value = "تم حفظ الإعدادات بنجاح."
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving settings", e)
                _uiNotice.value = "تعذر حفظ الإعدادات: ${e.localizedMessage}"
            }
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
