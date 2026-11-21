package com.example.data

import android.content.Context

data class MadrasaInfo(
    val name: String = MadrasaInfoStore.DEFAULT_NAME,
    val address: String = MadrasaInfoStore.DEFAULT_ADDRESS,
    val phone: String = MadrasaInfoStore.DEFAULT_PHONE
)

object MadrasaInfoStore {
    private const val PREFS_NAME = "madrasa_institution_prefs"
    private const val KEY_NAME = "madrasa_name"
    private const val KEY_ADDRESS = "madrasa_address"
    private const val KEY_PHONE = "madrasa_phone"

    const val DEFAULT_NAME = "ইসহাকিয়া কেরাতুল কোরআন নূরানী ও হাফেজিয়া মাদ্রাসা"
    const val DEFAULT_ADDRESS = "পশ্চিম তুষখালী, ছোটবিঘাই, পটুয়াখালী সদর, পটুয়াখালী"
    const val DEFAULT_PHONE = "+8801726541607"

    fun getInfo(context: Context): MadrasaInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_NAME, DEFAULT_NAME)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        val address = prefs.getString(KEY_ADDRESS, DEFAULT_ADDRESS)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS
        val phone = prefs.getString(KEY_PHONE, DEFAULT_PHONE)?.takeIf { it.isNotBlank() } ?: DEFAULT_PHONE
        return MadrasaInfo(name = name, address = address, phone = phone)
    }

    fun saveInfo(context: Context, name: String, address: String, phone: String): MadrasaInfo {
        val cleanInfo = MadrasaInfo(
            name = name.trim().ifBlank { DEFAULT_NAME },
            address = address.trim().ifBlank { DEFAULT_ADDRESS },
            phone = phone.trim().ifBlank { DEFAULT_PHONE }
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, cleanInfo.name)
            .putString(KEY_ADDRESS, cleanInfo.address)
            .putString(KEY_PHONE, cleanInfo.phone)
            .apply()
        return cleanInfo
    }
}
