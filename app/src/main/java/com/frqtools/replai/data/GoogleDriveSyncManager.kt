package com.frqtools.replai.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

object GoogleDriveSyncManager {
    private const val FILE_NAME = "replai_sync_data.json"
    private const val SCOPES = "oauth2:https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file"

    private val client = OkHttpClient()

    fun isUserSignedIn(context: Context): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    fun getSignedInEmail(context: Context): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    fun getGso(webClientId: String? = null): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.appdata"),
                Scope("https://www.googleapis.com/auth/drive.file")
            )
        if (!webClientId.isNullOrBlank()) {
            builder.requestIdToken(webClientId)
        }
        return builder.build()
    }

    fun getAccessToken(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return try {
            GoogleAuthUtil.getToken(context, account.account!!, SCOPES)
        } catch (e: Exception) {
            Log.e("GDriveSync", "Error getting access token", e)
            null
        }
    }

    fun findSyncFile(accessToken: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$FILE_NAME'&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GDriveSync", "Query files failed with code: ${response.code}")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val root = JSONObject(bodyStr)
                val files = root.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            Log.e("GDriveSync", "Exception finding sync file", e)
        }
        return null
    }

    fun downloadSyncFile(accessToken: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return response.body?.string()
                } else {
                    Log.e("GDriveSync", "Download file failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("GDriveSync", "Exception downloading file", e)
        }
        return null
    }

    fun createSyncFile(accessToken: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files"
        val jsonPayload = JSONObject()
        jsonPayload.put("name", FILE_NAME)
        val parents = org.json.JSONArray()
        parents.put("appDataFolder")
        jsonPayload.put("parents", parents)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return null
                    val obj = JSONObject(bodyStr)
                    return obj.getString("id")
                } else {
                    Log.e("GDriveSync", "Create file failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("GDriveSync", "Exception creating file", e)
        }
        return null
    }

    fun updateSyncFileContent(accessToken: String, fileId: String, content: String): Boolean {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = content.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .patch(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return true
                } else {
                    Log.e("GDriveSync", "Update file content failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("GDriveSync", "Exception updating file content", e)
        }
        return false
    }
}
