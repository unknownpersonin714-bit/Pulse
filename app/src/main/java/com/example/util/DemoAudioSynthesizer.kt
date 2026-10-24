package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.local.CachedTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Strictly isolated Demo Audio Synthesizer.
 * Disabled in normal operation. Only used when the user explicitly enables
 * "Isolated Demo / Test Audio Synthesizer" in Settings or on an empty emulator
 * to test real ExoPlayer PCM audio playback and synchronized LRC lyrics without
 * inserting fake tracks into the real device MediaStore library.
 */
object DemoAudioSynthesizer {

    data class GeneratedDemoBundle(
        val tracks: List<CachedTrackEntity>,
        val lrcByTrackId: Map<Long, String>
    )

    private data class SynthSpec(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val durationSec: Int,
        val baseFreqHz: Double,
        val chordRatios: List<Double>,
        val lrcContent: String
    )

    private val specs = listOf(
        SynthSpec(
            id = -9001L,
            title = "Subharmonic Horizon (432Hz Synth)",
            artist = "PULSE Acoustic Lab",
            album = "Monochrome Calibration Suite",
            albumId = -101L,
            durationSec = 24,
            baseFreqHz = 216.0,
            chordRatios = listOf(1.0, 1.25, 1.5, 2.0),
            lrcContent = """
                [00:00.00]PULSE Acoustic Calibration — Track 01
                [00:04.00]Fundamental harmonic carrier: 216 Hz / 432 Hz
                [00:08.00]Pure 16-bit PCM waveform synthesized locally on device
                [00:12.00]Testing Media3 ExoPlayer real-time seek and buffer accuracy
                [00:16.00]Synchronized LRC timestamp engine active
                [00:20.00]Offline signal path verified — zero network activity
            """.trimIndent()
        ),
        SynthSpec(
            id = -9002L,
            title = "Obsidian Resonance (A Minor Ambient)",
            artist = "PULSE Acoustic Lab",
            album = "Monochrome Calibration Suite",
            albumId = -101L,
            durationSec = 28,
            baseFreqHz = 220.0,
            chordRatios = listOf(1.0, 1.2, 1.5, 1.8),
            lrcContent = """
                [00:00.00]Obsidian Resonance — A Minor Pentatonic Study
                [00:05.00]Low-noise envelope modulation at 220 Hz
                [00:10.00]Designed for lock-screen and background session testing
                [00:16.00]Try reordering the queue or toggling Repeat One
                [00:22.00]Clean fade-out approaching terminal sample frame
            """.trimIndent()
        ),
        SynthSpec(
            id = -9003L,
            title = "Transient Pulse (Rhythmic Gate)",
            artist = "Waveform Architecture",
            album = "Studio Reference Signals",
            albumId = -102L,
            durationSec = 20,
            baseFreqHz = 196.0,
            chordRatios = listOf(1.0, 1.333, 1.5, 2.0),
            lrcContent = """
                [00:00.00]Transient Pulse — Rhythmic Gate Reference
                [00:04.00]120 BPM amplitude pulse modulation
                [00:08.00]Verifying gapless queue transition and audio focus
                [00:12.00]Add this track to Favourites or a custom Room Playlist
                [00:16.00]Final measure — preparing automatic next-track transition
            """.trimIndent()
        )
    )

    suspend fun generateSynthesizedDemoTracks(context: Context): GeneratedDemoBundle =
        withContext(Dispatchers.IO) {
            val demoDir = File(context.cacheDir, "pulse_demo_synth").apply { mkdirs() }
            val entities = mutableListOf<CachedTrackEntity>()
            val lyricsMap = mutableMapOf<Long, String>()
            val nowSec = System.currentTimeMillis() / 1000L

            specs.forEachIndexed { idx, spec ->
                val wavFile = File(demoDir, "pulse_synth_${-spec.id}.wav")
                if (!wavFile.exists() || wavFile.length() < 1024L) {
                    writeWarmChordWavFile(
                        targetFile = wavFile,
                        durationSec = spec.durationSec,
                        baseFreq = spec.baseFreqHz,
                        ratios = spec.chordRatios
                    )
                }
                entities.add(
                    CachedTrackEntity(
                        id = spec.id,
                        title = spec.title,
                        artist = spec.artist,
                        album = spec.album,
                        albumId = spec.albumId,
                        durationMs = spec.durationSec * 1000L,
                        contentUri = Uri.fromFile(wavFile).toString(),
                        albumArtUri = null,
                        dateAddedSec = nowSec - (idx * 60L),
                        trackNumber = idx + 1,
                        year = 2026,
                        sizeBytes = wavFile.length(),
                        folderPath = demoDir.absolutePath,
                        isAvailable = true,
                        isDemoTrack = true
                    )
                )
                lyricsMap[spec.id] = spec.lrcContent
            }

            GeneratedDemoBundle(tracks = entities, lrcByTrackId = lyricsMap)
        }

    private fun writeWarmChordWavFile(
        targetFile: File,
        durationSec: Int,
        baseFreq: Double,
        ratios: List<Double>
    ) {
        val sampleRate = 22050
        val numSamples = sampleRate * durationSec
        val dataSize = numSamples * 2 // 16-bit mono
        val totalDataLen = dataSize + 36

        FileOutputStream(targetFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM chunk size
            header.putShort(1) // PCM format
            header.putShort(1) // Mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // Byte rate
            header.putShort(2) // Block align
            header.putShort(16) // Bits per sample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val chunkSamples = 2048
            val sampleBuffer = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            var sampleIndex = 0
            while (sampleIndex < numSamples) {
                sampleBuffer.clear()
                val limit = minOf(chunkSamples, numSamples - sampleIndex)
                for (i in 0 until limit) {
                    val globalSample = sampleIndex + i
                    val t = globalSample.toDouble() / sampleRate.toDouble()
                    // Gentle envelope with slow LFO pulse
                    val attack = (t / 1.5).coerceAtMost(1.0)
                    val release = ((durationSec - t) / 2.0).coerceIn(0.0, 1.0)
                    val lfo = 0.72 + 0.28 * sin(2.0 * PI * 0.5 * t)

                    var signal = 0.0
                    for (ratio in ratios) {
                        signal += sin(2.0 * PI * (baseFreq * ratio) * t)
                    }
                    signal /= ratios.size.toDouble()

                    val amplitude = (signal * attack * release * lfo * 0.35 * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    sampleBuffer.putShort(amplitude)
                }
                fos.write(sampleBuffer.array(), 0, limit * 2)
                sampleIndex += limit
            }
        }
    }
}
