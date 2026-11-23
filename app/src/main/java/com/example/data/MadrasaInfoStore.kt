package com.example.data

import android.content.Context
import android.net.Uri
import java.io.File

data class MadrasaInfo(
    val name: String = MadrasaInfoStore.DEFAULT_NAME,
    val address: String = MadrasaInfoStore.DEFAULT_ADDRESS,
    val phone: String = MadrasaInfoStore.DEFAULT_PHONE,
    val logoPath: String = ""
)

object MadrasaInfoStore {
    private const val PREFS_NAME = "madrasa_institution_prefs"
    private const val KEY_NAME = "madrasa_name"
    private const val KEY_ADDRESS = "madrasa_address"
    private const val KEY_PHONE = "madrasa_phone"
    private const val KEY_LOGO_PATH = "madrasa_logo_path"

    const val DEFAULT_NAME = "ইসহাকিয়া কেরাতুল কোরআন নূরানী ও হাফেজিয়া মাদ্রাসা"
    const val DEFAULT_ADDRESS = "পশ্চিম তুষখালী, ছোটবিঘাই, পটুয়াখালী সদর, পটুয়াখালী"
    const val DEFAULT_PHONE = "+8801726541607"

    private fun resolveExistingLogoPath(rawPath: String): String {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) return ""
        return try {
            val uri = Uri.parse(trimmed)
            if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                val f = File(uri.path ?: trimmed)
                if (f.exists() && f.length() > 0L) trimmed else ""
            } else {
                trimmed
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun getInfo(context: Context): MadrasaInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_NAME, DEFAULT_NAME)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        val address = prefs.getString(KEY_ADDRESS, DEFAULT_ADDRESS)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS
        val phone = prefs.getString(KEY_PHONE, DEFAULT_PHONE)?.takeIf { it.isNotBlank() } ?: DEFAULT_PHONE
        val rawLogo = prefs.getString(KEY_LOGO_PATH, "") ?: ""
        val logoPath = resolveExistingLogoPath(rawLogo)
        return MadrasaInfo(name = name, address = address, phone = phone, logoPath = logoPath)
    }

    fun saveInfo(
        context: Context,
        name: String,
        address: String,
        phone: String,
        logoPath: String? = null
    ): MadrasaInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val finalLogoPath = if (logoPath != null) {
            resolveExistingLogoPath(logoPath)
        } else {
            resolveExistingLogoPath(prefs.getString(KEY_LOGO_PATH, "") ?: "")
        }

        val cleanInfo = MadrasaInfo(
            name = name.trim().ifBlank { DEFAULT_NAME },
            address = address.trim().ifBlank { DEFAULT_ADDRESS },
            phone = phone.trim().ifBlank { DEFAULT_PHONE },
            logoPath = finalLogoPath
        )
        prefs.edit()
            .putString(KEY_NAME, cleanInfo.name)
            .putString(KEY_ADDRESS, cleanInfo.address)
            .putString(KEY_PHONE, cleanInfo.phone)
            .putString(KEY_LOGO_PATH, cleanInfo.logoPath)
            .apply()
        return cleanInfo
    }

    fun saveLogoPath(context: Context, logoPath: String): MadrasaInfo {
        val current = getInfo(context)
        return saveInfo(
            context = context,
            name = current.name,
            address = current.address,
            phone = current.phone,
            logoPath = logoPath
        )
    }
}
