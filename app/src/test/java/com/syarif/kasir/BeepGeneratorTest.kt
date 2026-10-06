package com.syarif.kasir

import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class BeepGeneratorTest {

    @Test
    fun generateBeepWav() {
        val wavFile = File("C:/Users/Ach Syarif/AndroidStudioProjects/KASIR/app/src/main/res/raw/beeb.wav")
        if (wavFile.parentFile?.exists() != true) {
            wavFile.parentFile?.mkdirs()
        }
        if (!wavFile.exists()) {
            val sampleRate = 8000
            val durationSeconds = 0.15
            val numSamples = (sampleRate * durationSeconds).toInt()
            val dataSize = numSamples * 2 // 16-bit mono
            val fileSize = 36 + dataSize

            val byteBuffer = ByteArray(44 + dataSize)
            // RIFF header
            byteBuffer[0] = 'R'.code.toByte()
            byteBuffer[1] = 'I'.code.toByte()
            byteBuffer[2] = 'F'.code.toByte()
            byteBuffer[3] = 'F'.code.toByte()
            // File size
            putInt(byteBuffer, 4, fileSize)
            // WAVE
            byteBuffer[8] = 'W'.code.toByte()
            byteBuffer[9] = 'A'.code.toByte()
            byteBuffer[10] = 'V'.code.toByte()
            byteBuffer[11] = 'E'.code.toByte()
            // fmt chunk
            byteBuffer[12] = 'f'.code.toByte()
            byteBuffer[13] = 'm'.code.toByte()
            byteBuffer[14] = 't'.code.toByte()
            byteBuffer[15] = ' '.code.toByte()
            putInt(byteBuffer, 16, 16) // Subchunk1Size
            putShort(byteBuffer, 20, 1.toShort()) // AudioFormat (PCM)
            putShort(byteBuffer, 22, 1.toShort()) // NumChannels (Mono)
            putInt(byteBuffer, 24, sampleRate) // SampleRate
            putInt(byteBuffer, 28, sampleRate * 2) // ByteRate
            putShort(byteBuffer, 32, 2.toShort()) // BlockAlign
            putShort(byteBuffer, 34, 16.toShort()) // BitsPerSample
            // data chunk
            byteBuffer[36] = 'd'.code.toByte()
            byteBuffer[37] = 'a'.code.toByte()
            byteBuffer[38] = 't'.code.toByte()
            byteBuffer[39] = 'a'.code.toByte()
            putInt(byteBuffer, 40, dataSize)

            // Generate sine wave sample at 880Hz
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * 880.0 * i / sampleRate
                val sample = (Math.sin(angle) * 16383.0).toInt().toShort()
                putShort(byteBuffer, 44 + i * 2, sample)
            }

            FileOutputStream(wavFile).use { it.write(byteBuffer) }
        }
        org.junit.Assert.assertTrue(wavFile.exists())
    }

    private fun putInt(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xff).toByte()
        arr[offset + 1] = ((value shr 8) and 0xff).toByte()
        arr[offset + 2] = ((value shr 16) and 0xff).toByte()
        arr[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun putShort(arr: ByteArray, offset: Int, value: Short) {
        val v = value.toInt()
        arr[offset] = (v and 0xff).toByte()
        arr[offset + 1] = ((v shr 8) and 0xff).toByte()
    }
}
