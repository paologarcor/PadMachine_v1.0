package com.example.padmachine

import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    // 1. LAS VARIABLES GLOBALES VAN AQUÍ (FUERA DE ONCREATE)
    private lateinit var soundPool: SoundPool

    private var isEditMode = true // Estado global del modo

    val soundList = mutableListOf<Int>()

    val streamList = MutableList(9) { 0 }

    private var selectedPadIndex = -1

    private val labelIds = listOf(
        R.id.lbl1, R.id.lbl2, R.id.lbl3,
        R.id.lbl4, R.id.lbl5, R.id.lbl6,
        R.id.lbl7, R.id.lbl8, R.id.lbl9
    )

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (selectedPadIndex in 0..8) {
                contentResolver.openAssetFileDescriptor(it, "r")?.use { afd ->
                    val soundId = soundPool.load(afd, 1)
                    if (soundId != 0) {
                        soundList[selectedPadIndex] = soundId
                        Log.d("PAD", "Loaded sound from URI $it into pad ${selectedPadIndex + 1} (soundId: $soundId)")
                        updatePadLabel(selectedPadIndex, it)
                    } else {
                        Log.e("PAD", "Failed to load sound from URI $it")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. CONFIGURAR SOUNDPOOL (DENTRO DE ONCREATE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(9)
            .setAudioAttributes(audioAttributes)
            .build()

        // 4. CONFIGURAR BOTÓN DE MODO
        val editModeButton = findViewById<MaterialButton>(R.id.modeButton)
        editModeButton.text = if (isEditMode) "EDIT" else "LIVE"

        editModeButton.setOnClickListener {
            // Cambia el estado entre true y falso cada vez que se pulsa
            isEditMode = !isEditMode
            Log.d("MODE", "Modo Edit activo: $isEditMode")

            // Cambia el texto del botón: "EDIT" en modo edición, "LIVE" en modo live
            editModeButton.text = if (isEditMode) "EDIT" else "LIVE"
        }
        soundList.add(soundPool.load(this, R.raw.kick, 1))
        soundList.add(soundPool.load(this, R.raw.snare, 1))
        soundList.add(soundPool.load(this, R.raw.roll, 1))

        soundList.add(soundPool.load(this, R.raw.hat1, 1))
        soundList.add(soundPool.load(this, R.raw.hat2, 1))
        soundList.add(soundPool.load(this, R.raw.openhat, 1))

        soundList.add(soundPool.load(this, R.raw.hit1, 1))
        soundList.add(soundPool.load(this, R.raw.hit2, 1))
        soundList.add(soundPool.load(this, R.raw.hit3, 1))


        // 5. LISTA DE PADS Y CONFIGURACIÓN DE CLICS
        val pads = listOf(
            R.id.pad1, R.id.pad2, R.id.pad3,
            R.id.pad4, R.id.pad5, R.id.pad6,
            R.id.pad7, R.id.pad8, R.id.pad9
        )

        pads.forEachIndexed { index, id ->
            val pad = findViewById<MaterialButton>(id)

            // Usamos setOnTouchListener con ACTION_DOWN para eliminar la latencia de clic y responder al instante
            pad.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pad.performClick()
                        Log.d("PAD", "Pad ${index + 1} pulsado")

                        // Efecto visual de pulsación
                        pad.setBackgroundColor(Color.parseColor("#FF7E65"))

                        // 6. LÓGICA DE JUEGO / REPRODUCCIÓN / EDICIÓN
                        if (isEditMode) {
                            selectedPadIndex = index
                            pickAudioLauncher.launch(arrayOf("audio/*"))
                            Log.d("PAD", "Abriendo selector para Pad ${index + 1}")
                        } else {
                            // AQUÍ ES DONDE SE REPRODUCE EL SONIDO, AL HACER CLIC
                            soundPool.stop(streamList[index])

                            //Antes de reproducir detener el anterior
                            streamList[index] = soundPool.play(
                                soundList[index],
                                1f,
                                1f,
                                1,
                                0,
                                1f
                            )
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pad.postDelayed({
                            pad.setBackgroundColor(Color.parseColor("#F4DFDA"))
                        }, 100)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "Sound"
    }

    private fun updatePadLabel(index: Int, uri: Uri) {
        val fileName = getFileName(uri)
        findViewById<TextView>(labelIds[index])?.text = fileName
    }

    // 7. LIMPIEZA DE MEMORIA (FUERA DE ONCREATE)
    override fun onDestroy() {
        super.onDestroy()
        soundPool.release() // Evita que la app consuma batería/memoria en segundo plano
    }
}
