package com.example.util

object RoutineTimeUtil {

    private val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun toBengaliDigits(value: Int): String {
        return value.toString().map { ch ->
            if (ch in '0'..'9') bengaliDigits[ch - '0'] else ch
        }.joinToString("")
    }

    fun toAsciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = bengaliDigits.indexOf(ch)
            sb.append(if (idx >= 0) ('0' + idx) else ch)
        }
        return sb.toString()
    }

    /**
     * Parses time slot strings like "সকাল ৮:০০ - ৯:০০", "10:30 - 11:15", "১২:৩০ - ১:১৫"
     * and returns duration in minutes, or null if it cannot be determined.
     */
    fun calculateSlotMinutes(timeSlot: String): Int? {
        val normalized = toAsciiDigits(timeSlot)
        val regex = Regex("""(\d{1,2})\s*[:.]\s*(\d{2})""")
        val matches = regex.findAll(normalized).toList()
        if (matches.size < 2) return null

        val h1 = matches[0].groupValues[1].toIntOrNull() ?: return null
        val m1 = matches[0].groupValues[2].toIntOrNull() ?: return null
        val h2 = matches[1].groupValues[1].toIntOrNull() ?: return null
        val m2 = matches[1].groupValues[2].toIntOrNull() ?: return null

        if (m1 !in 0..59 || m2 !in 0..59 || h1 !in 0..23 || h2 !in 0..23) return null

        val startMin = h1 * 60 + m1
        var endMin = h2 * 60 + m2
        if (endMin <= startMin && h1 <= 12 && h2 <= 12) {
            endMin += 12 * 60
        }
        val diff = endMin - startMin
        return if (diff in 1..(12 * 60)) diff else null
    }

    fun formatMinutesBengali(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "-"
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${toBengaliDigits(hours)} ঘন্টা ${toBengaliDigits(mins)} মিনিট"
            hours > 0 -> "${toBengaliDigits(hours)} ঘন্টা"
            else -> "${toBengaliDigits(mins)} মিনিট"
        }
    }

    fun suggestDurationLabel(timeSlot: String): String {
        val mins = calculateSlotMinutes(timeSlot) ?: return ""
        return formatMinutesBengali(mins)
    }

    /**
     * Parses a duration label (e.g. "১ ঘন্টা ৩০ মিনিট", "45 মিনিট", "1 hr 20 min") to minutes.
     */
    fun parseDurationLabelMinutes(duration: String, fallbackTimeSlot: String = ""): Int {
        if (duration.isNotBlank()) {
            val ascii = toAsciiDigits(duration).lowercase()
            var total = 0
            var matched = false

            val hourRegex = Regex("""(\d+)\s*(ঘন্টা|ঘণ্টা|hr|hrs|hour|hours|h)""")
            val minRegex = Regex("""(\d+)\s*(মিনিট|min|mins|minute|minutes|m)""")

            hourRegex.find(ascii)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                total += it * 60
                matched = true
            }
            minRegex.find(ascii)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                total += it
                matched = true
            }
            if (matched && total > 0) return total

            // Plain number fallback (e.g. "45")
            val plainNum = Regex("""^\s*(\d+)\s*$""").find(ascii)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (plainNum != null && plainNum > 0) return plainNum
        }
        return calculateSlotMinutes(fallbackTimeSlot) ?: 0
    }

    fun resolveDurationDisplay(storedDuration: String, timeSlot: String): String {
        if (storedDuration.isNotBlank()) return storedDuration.trim()
        val mins = calculateSlotMinutes(timeSlot)
        return if (mins != null) formatMinutesBengali(mins) else "-"
    }
}
