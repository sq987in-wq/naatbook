package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NaatCategoriesTest {

    @Test
    fun `normalize maps current legacy blank and unknown categories`() {
        val matrix = listOf(
            null to NaatCategories.DEFAULT,
            "" to NaatCategories.DEFAULT,
            "   " to NaatCategories.DEFAULT,
            "Naat" to NaatCategories.NAAT,
            " naAt " to NaatCategories.NAAT,
            "HAMD" to NaatCategories.HAMD,
            "manqabat" to NaatCategories.MANQABAT,
            " Salam " to NaatCategories.SALAM,
            "qasida" to NaatCategories.QASIDA,
            "NASHEED" to NaatCategories.NASHEED,
            "my kalam" to NaatCategories.MY_KALAM,
            "OTHERS" to NaatCategories.OTHERS,
            "Hamd-o-Naat" to NaatCategories.NAAT,
            " SALAM & QASIDA " to NaatCategories.SALAM,
            "My Own Poetry" to NaatCategories.MY_KALAM,
            "audio only" to NaatCategories.MY_KALAM,
            "Unrecognized folder" to NaatCategories.OTHERS
        )

        matrix.forEach { (input, expected) ->
            assertEquals("normalize($input)", expected, NaatCategories.normalize(input))
        }
    }
}
