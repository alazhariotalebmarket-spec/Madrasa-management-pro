package com.example

import com.example.ui.screens.RESULT_SUBJECT_OPTIONS
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultSubjectOptionsTest {

    @Test
    fun `result subject dropdown contains exact 8 subjects in default order`() {
        assertEquals(
            listOf(
                "আরবী লিখিত",
                "আরবী মৌখিক",
                "বাংলা",
                "ইংরেজি",
                "অংক",
                "সাধারণ জ্ঞান",
                "আকাঈদ ও ফিকাহ",
                "দোয়া ও হাদিস"
            ),
            RESULT_SUBJECT_OPTIONS
        )
    }
}
