package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.repository.CallerContextInfo
import com.example.data.repository.CallLogRepository
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves caller identity by combining saved AI CallerProfiles with the device Contacts book
 * (when READ_CONTACTS is granted) so the AI Team distinguishes friends, clients, and unknown callers.
 */
class CallerIdentifier(
    private val context: Context,
    private val callLogRepository: CallLogRepository
) {
    companion object {
        private const val TAG = "CallerIdentifier"
    }

    suspend fun identifyCaller(phoneNumber: String): CallerContextInfo = withContext(Dispatchers.IO) {
        val cleanNumber = phoneNumber.trim()
        val savedProfile = if (cleanNumber.isNotBlank() && cleanNumber != "جلسة اختبار مباشر") {
            callLogRepository.getCallerProfile(cleanNumber)
        } else {
            null
        }

        val contactName = if (cleanNumber.isNotBlank() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            lookupContactName(cleanNumber)
        } else {
            ""
        }

        val effectiveProfile = when {
            savedProfile != null -> savedProfile
            contactName.isNotBlank() -> CallerProfile(
                phoneNumber = cleanNumber,
                displayName = contactName,
                category = CallerCategory.FRIEND,
                notes = "مسجل في جهات الاتصال"
            )
            else -> null
        }

        val insights = callLogRepository.getRecentInsights(limit = 5)

        CallerContextInfo(
            phoneNumber = cleanNumber,
            resolvedName = contactName.ifBlank { savedProfile?.displayName.orEmpty() },
            savedProfile = effectiveProfile,
            isInContacts = contactName.isNotBlank(),
            recentInsights = insights
        )
    }

    private fun lookupContactName(phoneNumber: String): String {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).orEmpty()
                } else {
                    ""
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Contacts lookup skipped", e)
            ""
        }
    }
}
