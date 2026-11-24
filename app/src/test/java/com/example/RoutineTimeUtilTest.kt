package com.example

import com.example.data.entity.ClassRoutineEntity
import com.example.util.RoutineTimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTimeUtilTest {

    @Test
    fun `sortRoutinesByClassAndSerial orders classes progressively and serials ascending`() {
        val items = listOf(
            ClassRoutineEntity(
                id = 1,
                className = "হিফজ",
                dayOfWeek = "সারা বছর",
                timeSlot = "৯:০০ - ১০:০০",
                subject = "দাওর",
                teacherName = "মাও. করিম",
                serialOrder = 2
            ),
            ClassRoutineEntity(
                id = 2,
                className = "নূরানী - প্রথম",
                dayOfWeek = "সারা বছর",
                timeSlot = "৮:০০ - ৯:০০",
                subject = "কুরআন",
                teacherName = "হাফেজ রহিম",
                serialOrder = 2
            ),
            ClassRoutineEntity(
                id = 3,
                className = "নূরানী - প্রথম",
                dayOfWeek = "সারা বছর",
                timeSlot = "৭:০০ - ৮:০০",
                subject = "কায়দা",
                teacherName = "হাফেজ রহিম",
                serialOrder = 1
            ),
            ClassRoutineEntity(
                id = 4,
                className = "হিফজ",
                dayOfWeek = "সারা বছর",
                timeSlot = "৮:০০ - ৯:০০",
                subject = "সবক",
                teacherName = "মাও. করিম",
                serialOrder = 1
            )
        )

        val sorted = RoutineTimeUtil.sortRoutinesByClassAndSerial(items)
        assertEquals(listOf(3, 2, 4, 1), sorted.map { it.id })
    }

    @Test
    fun `sortSingleClassBySerial falls back to time order when serialOrder matches`() {
        val items = listOf(
            ClassRoutineEntity(
                id = 10,
                className = "কিতাব - মীযান",
                dayOfWeek = "সারা বছর",
                timeSlot = "১০:০০ - ১১:০০",
                subject = "নাহু",
                teacherName = "মাও. আজাদ",
                serialOrder = 0
            ),
            ClassRoutineEntity(
                id = 11,
                className = "কিতাব - মীযান",
                dayOfWeek = "সারা বছর",
                timeSlot = "৮:০০ - ৯:০০",
                subject = "সরফ",
                teacherName = "মাও. আজাদ",
                serialOrder = 0
            )
        )

        val sorted = RoutineTimeUtil.sortSingleClassBySerial(items)
        assertEquals(listOf(11, 10), sorted.map { it.id })
    }

    @Test
    fun `parseDurationLabelMinutes extracts Bengali and English durations`() {
        assertEquals(45, RoutineTimeUtil.parseDurationLabelMinutes("৪৫ মিনিট"))
        assertEquals(60, RoutineTimeUtil.parseDurationLabelMinutes("১ ঘণ্টা"))
        assertTrue(RoutineTimeUtil.classSortRank("নূরানী - শিশু") < RoutineTimeUtil.classSortRank("হিফজ"))
    }
}
