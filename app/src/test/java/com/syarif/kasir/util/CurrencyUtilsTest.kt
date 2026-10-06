package com.syarif.kasir.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun testToRupiahFormatting() {
        val price1 = 10000.0
        val formatted1 = price1.toRupiah()
        assertTrue(formatted1.contains("10.000") || formatted1.contains("10,000") || formatted1.contains("Rp"))

        val price2 = 8500.0
        val formatted2 = price2.toRupiah()
        assertTrue(formatted2.contains("8.500") || formatted2.contains("8,500") || formatted2.contains("Rp"))
    }

    @Test
    fun testCalculateSisa() {
        // Test with DP > 0: sisa = totalHarga - dp
        val sisaDp = calculateSisa(totalHarga = 100000.0, dp = 30000.0, metodePembayaran = "bon", rawSisa = 0.0)
        assertEquals(70000.0, sisaDp, 0.001)

        // Test with bon method and sisa <= 0 (no DP): fallback to totalHarga
        val sisaBonNoDp = calculateSisa(totalHarga = 150000.0, dp = 0.0, metodePembayaran = "bon", rawSisa = 0.0)
        assertEquals(150000.0, sisaBonNoDp, 0.001)

        // Test with bon method and existing valid rawSisa
        val sisaBonExisting = calculateSisa(totalHarga = 150000.0, dp = 0.0, metodePembayaran = "bon", rawSisa = 50000.0)
        assertEquals(50000.0, sisaBonExisting, 0.001)

        // Test with tunai method and sisa = 0
        val sisaTunai = calculateSisa(totalHarga = 50000.0, dp = 0.0, metodePembayaran = "Tunai", rawSisa = 0.0)
        assertEquals(0.0, sisaTunai, 0.001)
    }
}

