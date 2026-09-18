package com.example.palashtranslator

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.sentencepiece.Model
import com.sentencepiece.Scoring
import com.sentencepiece.SentencePieceAlgorithm
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer
import java.nio.file.Paths

class IndicTranslator(context: Context) {

    companion object {
        private const val TAG = "IndicTranslator"

        private const val ASSET_DIR = "file_tra"

        private const val SRC_LANG = "hin_Deva"
        private const val TGT_LANG = "sat_Olck"

        private const val MAX_SOURCE_LENGTH = 256
        private const val MAX_OUTPUT_LENGTH = 80
    }

    private val env =
        OrtEnvironment.getEnvironment()

    private val modelDir =
        File(context.filesDir, ASSET_DIR)

    private val encoderSession: OrtSession
    private val decoderSession: OrtSession
    private val decoderWithPastSession: OrtSession

    private val srcVocab =
        HashMap<String, Int>()
    private val spmToModelSrc =
        ArrayList<Int>()

    private val tgtVocab =
        HashMap<Int, String>()

    private lateinit var srcSentencePiece: Model
    private lateinit var tgtSentencePiece: Model

    private val spAlgorithm =
        SentencePieceAlgorithm(
            true,
            Scoring.HIGHEST_SCORE
        )

    private val eosId: Int
    private val decoderStartId: Int

    init {
        Log.d(TAG, "Initializing IndicTranslator...")

        copyAssets(context)
        loadVocabularies()

        srcSentencePiece =
            Model.parseFrom(
                Paths.get(
                    File(
                        modelDir,
                        "model.SRC"
                    ).absolutePath
                )
            )

        tgtSentencePiece =
            Model.parseFrom(
                Paths.get(
                    File(
                        modelDir,
                        "model.TGT"
                    ).absolutePath
                )
            )

        eosId =
            srcVocab["</s>"]
                ?: error(
                    "</s> missing from dict.SRC.json"
                )

        decoderStartId =
            readDecoderStartId()

        val options =
            OrtSession.SessionOptions().apply {

                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)

                setOptimizationLevel(
                    OrtSession.SessionOptions.OptLevel.BASIC_OPT
                )
            }

        val encoderFile =
            File(
                modelDir,
                "encoder_model.onnx"
            )

        val decoderFile =
            File(
                modelDir,
                "decoder_model.onnx"
            )

        val decoderPastFile =
            File(
                modelDir,
                "decoder_with_past_model.onnx"
            )

        Log.d(TAG, "Loading encoder...")

        encoderSession =
            env.createSession(
                encoderFile.absolutePath,
                options
            )

        Log.d(TAG, "Loading decoder...")

        decoderSession =
            env.createSession(
                decoderFile.absolutePath,
                options
            )

        Log.d(TAG, "Loading decoder_with_past...")

        decoderWithPastSession =
            env.createSession(
                decoderPastFile.absolutePath,
                options
            )

        Log.d(
            TAG,
            "IndicTranslator initialized successfully."
        )

        Log.d(
            TAG,
            "Encoder inputs = ${encoderSession.inputNames}"
        )

        Log.d(
            TAG,
            "Decoder inputs = ${decoderSession.inputNames}"
        )

        Log.d(
            TAG,
            "Decoder-with-past inputs = ${decoderWithPastSession.inputNames}"
        )

        Log.d(
            TAG,
            "Decoder outputs = ${decoderSession.outputNames}"
        )
    }

    private fun copyAssets(context: Context) {

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val files =
            listOf(
                "config.json",
                "generation_config.json",

                "encoder_model.onnx",
                "encoder_model.onnx.data",

                "decoder_model.onnx",
                "decoder_shared.onnx.data",
                "decoder_with_past_model.onnx",

                "dict.SRC.json",
                "dict.TGT.json",

                "model.SRC",
                "model.TGT",

                "special_tokens_map.json",
                "tokenization_indictrans.py",
                "tokenizer_config.json",
                "tokenizer_meta.json",
                "spm_to_model_src.json",

                "tokenizer_src.json",
                "tokenizer_tgt.json"
            )

        for (name in files) {

            val destination =
                File(
                    modelDir,
                    name
                )

            if (
                destination.exists() &&
                destination.length() > 0
            ) {
                continue
            }

            Log.d(
                TAG,
                "Copying asset: $name"
            )

            context.assets
                .open(
                    "$ASSET_DIR/$name"
                )
                .use { input ->

                    destination
                        .outputStream()
                        .use { output ->

                            input.copyTo(output)
                        }
                }
        }

        Log.d(
            TAG,
            "Asset copy complete."
        )
    }

    private fun loadVocabularies() {

        val srcFile =
            File(
                modelDir,
                "dict.SRC.json"
            )

        val tgtFile =
            File(
                modelDir,
                "dict.TGT.json"
            )

        val srcObject =
            JSONObject(
                srcFile.readText()
            )

        val tgtObject =
            JSONObject(
                tgtFile.readText()
            )

        val srcKeys =
            srcObject.keys()

        while (srcKeys.hasNext()) {

            val token =
                srcKeys.next()

            srcVocab[token] =
                srcObject.getInt(token)
        }

        val tgtKeys =
            tgtObject.keys()

        while (tgtKeys.hasNext()) {

            val token =
                tgtKeys.next()

            val id =
                tgtObject.getInt(token)

            tgtVocab[id] =
                token
        }

        Log.d(
            TAG,
            "SRC vocab size = ${srcVocab.size}"
        )

        Log.d(
            TAG,
            "TGT vocab size = ${tgtVocab.size}"
        )

        val mappingFile =
            File(
                modelDir,
                "spm_to_model_src.json"
            )

        val mappingArray =
            org.json.JSONArray(
                mappingFile.readText()
            )

        spmToModelSrc.clear()

        for (i in 0 until mappingArray.length()) {
            spmToModelSrc.add(
                mappingArray.getInt(i)
            )
        }

        Log.d(
            TAG,
            "SPM → model mapping size = ${spmToModelSrc.size}"
        )
    }

    private fun readDecoderStartId(): Int {

        val file =
            File(
                modelDir,
                "generation_config.json"
            )

        if (!file.exists()) {
            return 2
        }

        return try {

            val json =
                JSONObject(
                    file.readText()
                )

            json.optInt(
                "decoder_start_token_id",
                2
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Could not read generation_config.json"
            )

            2
        }
    }


    private fun tokenizeSource(
        hindiText: String
    ): LongArray {

        val srcLangId =
            srcVocab[SRC_LANG]
                ?: error(
                    "$SRC_LANG missing from SRC vocabulary"
                )

        val tgtLangId =
            srcVocab[TGT_LANG]
                ?: error(
                    "$TGT_LANG missing from SRC vocabulary"
                )

        val text =
            hindiText
                .trim()
                .replace(
                    Regex("\\s+"),
                    " "
                )

        val spIds =
            srcSentencePiece.encodeNormalized(
                text,
                spAlgorithm
            )

        val ids =
            ArrayList<Long>()

        ids.add(
            srcLangId.toLong()
        )

        ids.add(
            tgtLangId.toLong()
        )

        for (spId in spIds) {
            val modelId =
                if (spId in spmToModelSrc.indices) {
                    spmToModelSrc[spId]
                } else {
                    srcVocab["<unk>"] ?: 3
                }

            ids.add(modelId.toLong())
        }

        ids.add(
            eosId.toLong()
        )

        if (ids.size <= MAX_SOURCE_LENGTH) {
            return ids.toLongArray()
        }

        val trimmed =
            ids
                .take(
                    MAX_SOURCE_LENGTH - 1
                )
                .toMutableList()

        trimmed.add(
            eosId.toLong()
        )

        return trimmed.toLongArray()
    }

    fun translate(
        hindiText: String
    ): String {

        if (hindiText.isBlank()) {
            return ""
        }

        Log.d(
            TAG,
            "translate() called with: $hindiText"
        )

        var inputTensor: OnnxTensor? = null
        var attentionTensor: OnnxTensor? = null

        var encoderResult: OrtSession.Result? = null
        var previousDecoderResult: OrtSession.Result? = null

        try {

            val inputIds =
                tokenizeSource(
                    hindiText
                )

            Log.d(
                TAG,
                "Source IDs = ${inputIds.contentToString()}"
            )

            val attentionMask =
                LongArray(
                    inputIds.size
                ) { 1L }

            val shape =
                longArrayOf(
                    1L,
                    inputIds.size.toLong()
                )

            inputTensor =
                OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(inputIds),
                    shape
                )

            attentionTensor =
                OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(attentionMask),
                    shape
                )


            Log.d(
                TAG,
                "Running encoder..."
            )

            encoderResult =
                encoderSession.run(
                    mapOf(
                        "input_ids" to inputTensor!!,
                        "attention_mask" to attentionTensor!!
                    )
                )

            val encoderHiddenStates =
                encoderResult
                    ?.get("last_hidden_state")
                    ?.get() as? OnnxTensor
                    ?: error(
                        "last_hidden_state missing"
                    )

            Log.d(
                TAG,
                "Encoder complete."
            )


            var nextToken =
                decoderStartId

            val generatedIds =
                ArrayList<Int>()

            for (
            step in 0 until MAX_OUTPUT_LENGTH
            ) {

                val decoderInput =
                    OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(
                            longArrayOf(
                                nextToken.toLong()
                            )
                        ),
                        longArrayOf(
                            1L,
                            1L
                        )
                    )

                var currentResult:
                        OrtSession.Result? = null

                try {

                    if (step == 0) {


                        Log.d(
                            TAG,
                            "Running first decoder..."
                        )

                        currentResult =
                            decoderSession.run(
                                mapOf(
                                    "input_ids"
                                            to decoderInput,

                                    "encoder_attention_mask"
                                            to attentionTensor!!,

                                    "encoder_hidden_states"
                                            to encoderHiddenStates
                                )
                            )

                    } else {


                        Log.d(
                            TAG,
                            "Running decoder-with-past, step=$step"
                        )

                        val oldResult =
                            previousDecoderResult
                                ?: error(
                                    "Previous decoder result missing"
                                )

                        val feeds =
                            HashMap<String, OnnxTensor>()

                        feeds["input_ids"] =
                            decoderInput

                        feeds["encoder_attention_mask"] =
                            attentionTensor!!



                        for (
                        outputName
                        in decoderSession.outputNames
                        ) {

                            if (
                                !outputName.startsWith(
                                    "present."
                                )
                            ) {
                                continue
                            }

                            val inputName =
                                outputName.replace(
                                    "present.",
                                    "past_key_values."
                                )

                            val tensor =
                                oldResult
                                    .get(outputName)
                                    .get()
                                        as? OnnxTensor
                                    ?: continue

                            feeds[inputName] =
                                tensor
                        }

                        currentResult =
                            decoderWithPastSession.run(
                                feeds
                            )


                        try {
                            oldResult.close()
                        } catch (_: Exception) {
                        }

                        previousDecoderResult =
                            null
                    }

                    val logitsTensor =
                        currentResult!!
                            .get("logits")
                            .get() as? OnnxTensor
                            ?: error(
                                "Decoder logits missing"
                            )

                    @Suppress("UNCHECKED_CAST")
                    val logits =
                        logitsTensor.value
                                as Array<Array<FloatArray>>

                    val lastLogits =
                        logits[0][
                            logits[0].size - 1
                        ]

                    val predictedId =
                        argMax(lastLogits)

                    generatedIds.add(
                        predictedId
                    )

                    Log.d(
                        TAG,
                        "step=$step token=$predictedId"
                    )

                    if (
                        predictedId == eosId
                    ) {

                        Log.d(
                            TAG,
                            "EOS reached."
                        )

                        /*
                         * No need to retain the current
                         * result after EOS.
                         */
                        currentResult.close()
                        currentResult = null

                        break
                    }


                    previousDecoderResult =
                        currentResult

                    currentResult = null

                    nextToken =
                        predictedId

                } finally {

                    decoderInput.close()

                    try {
                        currentResult?.close()
                    } catch (_: Exception) {
                    }
                }
            }


            Log.d(
                TAG,
                "Generated IDs = $generatedIds"
            )

            val outputTokens =
                generatedIds.mapNotNull { id ->
                    tgtVocab[id]
                }.filter { token ->
                    token != "<s>" &&
                            token != "</s>" &&
                            token != "<pad>"
                }

            val rawOutput =
                outputTokens
                    .joinToString("")
                    .replace("▁", " ")
                    .replace(
                        Regex("\\s+"),
                        " "
                    )
                    .trim()

            Log.d(
                TAG,
                "Raw decoded output = $rawOutput"
            )

            return rawOutput

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Translation failed",
                e
            )

            return "Translation error: ${e.message}"

        } finally {

            try {
                previousDecoderResult?.close()
            } catch (_: Exception) {
            }

            try {
                encoderResult?.close()
            } catch (_: Exception) {
            }

            try {
                inputTensor?.close()
            } catch (_: Exception) {
            }

            try {
                attentionTensor?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun argMax(
        values: FloatArray
    ): Int {

        var bestIndex = 0
        var bestValue = values[0]

        for (
        i in 1 until values.size
        ) {

            if (
                values[i] > bestValue
            ) {

                bestValue =
                    values[i]

                bestIndex =
                    i
            }
        }

        return bestIndex
    }

    fun close() {

        try {
            encoderSession.close()
        } catch (_: Exception) {
        }

        try {
            decoderSession.close()
        } catch (_: Exception) {
        }

        try {
            decoderWithPastSession.close()
        } catch (_: Exception) {
        }

        try {
            env.close()
        } catch (_: Exception) {
        }
    }
}