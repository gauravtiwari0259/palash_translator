package com.example.palashtranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.palashtranslator.ui.theme.PalashTranslatorTheme
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class MainActivity : ComponentActivity(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var translator: IndicTranslator? = null

    private var transcriptState =
        mutableStateOf("Ready")

    private var translationState =
        mutableStateOf("Loading translator...")

    private val microphonePermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startVosk()
            } else {
                transcriptState.value =
                    "Microphone permission denied"
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /*
         * Initialize the ONNX translator in the background.
         */
        Thread {
            try {

                val loadedTranslator =
                    IndicTranslator(this)

                translator =
                    loadedTranslator

                runOnUiThread {
                    translationState.value =
                        "Translator ready"
                }

            } catch (e: Exception) {

                e.printStackTrace()

                runOnUiThread {
                    translationState.value =
                        "Translator error: ${e.message}"
                }
            }
        }.start()

        setContent {
            PalashTranslatorTheme {
                RecordingScreen()
            }
        }
    }

    @Composable
    private fun RecordingScreen() {

        var isRecording by remember {
            mutableStateOf(false)
        }

        PremiumTranslatorUI(
            isRecording = isRecording,

            sourceText = transcriptState.value,

            translatedText =
                translationState.value,

            onRecordToggle = {

                if (isRecording) {

                    stopVosk()

                    isRecording =
                        false

                } else {

                    if (
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        microphonePermission.launch(
                            Manifest.permission.RECORD_AUDIO
                        )

                    } else {

                        startVosk()
                    }

                    isRecording =
                        true
                }
            },

            onSwapLanguages = {

                /*
                 * Currently only:
                 *
                 * Hindi → Santali
                 *
                 * is supported by our model.
                 *
                 * Reverse translation can be added later.
                 */

            }
        )
    }

    @Composable
    private fun PremiumTranslatorUI(
        isRecording: Boolean,
        sourceText: String,
        translatedText: String,
        onRecordToggle: () -> Unit,
        onSwapLanguages: () -> Unit
    ) {

        val micColor by animateColorAsState(
            targetValue =
                if (isRecording) {
                    Color(0xFFEF4444)
                } else {
                    Color(0xFF3B82F6)
                },
            label = ""
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF0F172A)
                )
                .padding(24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            /*
             * APP TITLE
             */

            Text(
                text = "PALASH Translator",

                color =
                    Color.White,

                fontSize =
                    28.sp,

                fontWeight =
                    FontWeight.ExtraBold,

                modifier =
                    Modifier.padding(
                        top = 32.dp,
                        bottom = 32.dp
                    )
            )

            /*
             * HINDI CARD
             */

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFF1E293B)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "HINDI",

                        color =
                            Color(0xFF94A3B8),

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            sourceText,

                        color =
                            Color.White,

                        fontSize =
                            20.sp
                    )
                }
            }

            /*
             * SWAP BUTTON
             */

            IconButton(

                onClick =
                    onSwapLanguages,

                modifier =
                    Modifier
                        .padding(vertical = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF1E293B)
                        )
            ) {

                Icon(

                    imageVector =
                        Icons.Rounded.SwapVert,

                    contentDescription =
                        "Swap languages",

                    tint =
                        Color.White
                )
            }

            /*
             * SANTALI CARD
             */

            Card(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFF3B82F6)
                                .copy(alpha = 0.1f)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(

                        text =
                            "SANTALI",

                        color =
                            Color(0xFF60A5FA),

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(

                        text =
                            translatedText,

                        color =
                            Color.White,

                        fontSize =
                            20.sp
                    )
                }
            }

            /*
             * SPACE BEFORE MIC
             */

            Spacer(
                modifier =
                    Modifier.height(40.dp)
            )

            /*
             * MICROPHONE BUTTON
             */

            FloatingActionButton(

                onClick =
                    onRecordToggle,

                modifier =
                    Modifier.size(80.dp),

                containerColor =
                    micColor,

                shape =
                    CircleShape
            ) {

                Icon(

                    imageVector =
                        Icons.Rounded.Mic,

                    contentDescription =
                        if (isRecording) {
                            "Stop recording"
                        } else {
                            "Start recording"
                        },

                    tint =
                        Color.White,

                    modifier =
                        Modifier.size(36.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(32.dp)
            )
        }
    }

    private fun startVosk() {

        Thread {

            try {

                val modelDir =
                    File(
                        filesDir,
                        "model-hi"
                    )

                /*
                 * Copy Hindi Vosk model only
                 * if it is not already present.
                 */

                if (!modelDir.exists()) {

                    copyAssetFolder(
                        "model-hi",
                        modelDir
                    )
                }

                model =
                    Model(
                        modelDir.absolutePath
                    )

                val recognizer =
                    Recognizer(
                        model,
                        16000.0f
                    )

                runOnUiThread {

                    transcriptState.value =
                        "Listening..."
                }

                speechService =
                    SpeechService(
                        recognizer,
                        16000.0f
                    )

                speechService?.startListening(
                    this
                )

            } catch (e: Exception) {

                e.printStackTrace()

                runOnUiThread {

                    transcriptState.value =
                        "Vosk error: ${e.message}"
                }
            }

        }.start()
    }

    private fun stopVosk() {

        speechService?.stop()

        speechService?.shutdown()

        speechService =
            null

        runOnUiThread {

            if (
                transcriptState.value ==
                "Listening..."
            ) {

                transcriptState.value =
                    "Ready"
            }
        }
    }

    override fun onPartialResult(
        hypothesis: String?
    ) {

        if (
            !hypothesis.isNullOrBlank()
        ) {

            val text =
                extractTextFromJson(
                    hypothesis
                )

            if (text.isNotEmpty()) {

                runOnUiThread {

                    transcriptState.value =
                        text
                }
            }
        }
    }

    override fun onResult(
        hypothesis: String?
    ) {

        handleFinalResult(
            hypothesis
        )
    }

    override fun onFinalResult(
        hypothesis: String?
    ) {

        handleFinalResult(
            hypothesis
        )
    }

    private fun handleFinalResult(
        hypothesis: String?
    ) {

        if (
            hypothesis.isNullOrBlank()
        ) {
            return
        }

        val hindiText =
            extractTextFromJson(
                hypothesis
            )

        if (
            hindiText.isEmpty()
        ) {
            return
        }

        runOnUiThread {

            transcriptState.value =
                hindiText
        }

        /*
         * Translation runs away from
         * the UI thread.
         */

        Thread {

            try {

                val currentTranslator =
                    translator

                if (
                    currentTranslator == null
                ) {

                    runOnUiThread {

                        translationState.value =
                            "Translator not ready"
                    }

                    return@Thread
                }

                val result =
                    currentTranslator.translate(
                        hindiText
                    )

                runOnUiThread {

                    translationState.value =
                        result
                }

            } catch (e: Exception) {

                e.printStackTrace()

                runOnUiThread {

                    translationState.value =
                        "Translation error: ${e.message}"
                }
            }

        }.start()
    }

    private fun extractTextFromJson(
        jsonString: String
    ): String {

        return try {

            val jsonObject =
                JSONObject(
                    jsonString
                )

            when {

                jsonObject.has("text") ->
                    jsonObject.getString(
                        "text"
                    )

                jsonObject.has("partial") ->
                    jsonObject.getString(
                        "partial"
                    )

                else ->
                    ""

            }

        } catch (e: Exception) {

            ""
        }
    }

    override fun onError(
        exception: Exception?
    ) {

        runOnUiThread {

            transcriptState.value =
                "Recognition error: ${exception?.message}"
        }
    }

    override fun onTimeout() {

        stopVosk()
    }

    private fun copyAssetFolder(
        assetPath: String,
        destination: File
    ) {

        destination.mkdirs()

        val files =
            assets.list(
                assetPath
            ) ?: return

        for (file in files) {

            val sourcePath =
                "$assetPath/$file"

            val destinationFile =
                File(
                    destination,
                    file
                )

            val children =
                assets.list(
                    sourcePath
                )

            if (
                !children.isNullOrEmpty()
            ) {

                copyAssetFolder(
                    sourcePath,
                    destinationFile
                )

            } else {

                assets
                    .open(sourcePath)
                    .use { input ->

                        destinationFile
                            .outputStream()
                            .use { output ->

                                input.copyTo(
                                    output
                                )
                            }
                    }
            }
        }
    }

    override fun onDestroy() {

        stopVosk()

        model?.close()

        model =
            null

        translator?.close()

        translator =
            null

        super.onDestroy()
    }
}