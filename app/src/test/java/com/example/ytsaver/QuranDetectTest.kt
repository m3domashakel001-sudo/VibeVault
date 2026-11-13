package com.example.ytsaver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranDetectTest {
    @Test fun arabicAndEnglishWordsAreRecognised() {
        assertTrue(QuranDetect.isQuran("سورة البقرة - مشاري العفاسي"))
        assertTrue(QuranDetect.isQuran("Surah Al-Kahf recitation"))
        assertTrue(QuranDetect.isQuran("Quran - Abdul Basit"))
    }

    @Test fun ordinarySongsAreNot() {
        assertFalse(QuranDetect.isQuran("Elissa - Sahla El Aziya"))
        assertFalse(QuranDetect.isQuran("Insurance Blues"))      // "sura" inside another word must not match
        assertFalse(QuranDetect.isQuran(""))
    }
}
