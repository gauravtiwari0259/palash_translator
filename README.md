# PALASH Translator

> **An offline, on-device Android application that converts Hindi speech into Santali text in the Ol Chiki script.**

PALASH Translator is a fully offline Android application designed around a practical problem: **how can modern AI translation be deployed in remote environments where internet connectivity is unreliable and the available devices have limited hardware resources?**

The application takes spoken Hindi, performs speech recognition locally, translates the resulting Hindi text into Santali using a local neural machine-translation model, and displays the output in the **Ol Chiki script**.

The complete pipeline runs directly on the Android device without requiring a cloud translation API.

---

## 🎥 Demo

[▶️ Watch PALASH Translator demo](https://github.com/gauravtiwari0259/palash_translator/issues/1#issue-5502467492)

# The Problem

Jharkhand's remote tribal villages can face limited or unreliable internet connectivity, while the devices available in such environments may have relatively constrained hardware.

This creates a practical deployment problem.

A large cloud-based AI model may provide strong results, but it is not useful when:

- Internet connectivity is unavailable
- Cloud APIs cannot be relied upon
- Devices have limited RAM and storage
- Large models cannot realistically run locally

Instead of building a system that depends on a server, this project explores the opposite approach:

> **Can a useful multilingual AI pipeline be made small and efficient enough to run directly on a low-resource Android device?**

That became the main engineering focus of PALASH Translator.

---

# Solution

PALASH Translator uses a combination of pretrained models and on-device inference technologies to create a completely local pipeline.

```text
                  🎤 Hindi Speech
                        │
                        ▼
              ┌───────────────────┐
              │   Vosk Hindi ASR  │
              └─────────┬─────────┘
                        │
                        ▼
                    Hindi Text
                        │
                        ▼
              SentencePiece Tokenizer
                        │
                        ▼
             SPM → IndicTrans2 ID Map
                        │
                        ▼
              ┌───────────────────┐
              │ IndicTrans2 INT8  │
              │     ONNX Model    │
              └─────────┬─────────┘
                        │
                        ▼
               Autoregressive
                  Decoding
                        │
                        ▼
                Santali Text
                        │
                        ▼
                 Ol Chiki Script
                        │
                        ▼
                   Android UI
````

Everything above runs **locally on the phone**.

No speech needs to be sent to a cloud speech API and no translation API is required for the core pipeline.

---

# Key Features

* Offline Hindi speech recognition
* Hindi → Santali translation
* ᱥᱟᱱᱛᱟᱲᱤ output in the Ol Chiki script
* Fully on-device inference
* ONNX Runtime Android
* INT8 translation model
* Custom tokenizer / vocabulary mapping
* Models packaged locally with the application
* No cloud translation API required
* Works without internet during inference
* Current complete application footprint is approximately **775 MB**
* Successfully tested on a **3 GB RAM Android device**

---

# What I Worked On

The underlying ASR and machine-translation models used in this project are publicly available pretrained models.

The main contribution of this project was **not training a large model from scratch**, but taking existing models and figuring out how to make them practical for the target environment.

The main work involved:

### Model Research

I evaluated multiple pretrained model options to understand:

* Translation quality
* Language support
* Model size
* Mobile deployment feasibility
* ONNX compatibility
* Runtime behaviour
* Suitability for Hindi → Santali translation

The goal was not simply to find a model that worked on a PC.

The goal was to find a model that could **actually be deployed and run locally on a constrained Android device**.

### Model Selection

After testing different approaches, the current pipeline uses:

* Vosk for Hindi speech recognition
* IndicTrans2 for Hindi → Santali translation
* An INT8 ONNX representation for mobile inference
* ONNX Runtime for Android execution

### Mobile Deployment

A major part of the work was taking the selected models and integrating them into a real Android application.

This included:

* ONNX model deployment
* Android asset packaging
* ONNX Runtime integration
* SentencePiece integration
* Vocabulary-ID conversion
* Transformer decoder integration
* On-device inference
* Physical-device testing

### Debugging the Model Pipeline

One of the biggest technical problems was a mismatch between the native SentencePiece vocabulary IDs and the vocabulary IDs expected by IndicTrans2.

This initially produced invalid output.

I traced the problem and implemented a custom mapping layer between the two vocabularies.

---

# Technology Stack

## Android

* Kotlin
* Jetpack Compose
* Material 3
* Android SDK
* Java 11

## Machine Learning

* IndicTrans2
* Vosk
* SentencePiece
* ONNX
* ONNX Runtime

## Deployment

* INT8 ONNX inference
* On-device inference
* Android asset packaging
* Autoregressive transformer decoding

---

# Models Used

## 1. Hindi Speech Recognition

### Model

```text
vosk-model-small-hi-0.22
```

Used for:

```text
Hindi Speech
      ↓
Hindi Text
```

The model is bundled inside the application:

```text
app/src/main/assets/model-hi/
```

Speech recognition runs locally on the Android device.

---

# 2. Machine Translation

## Base Model

```text
ai4bharat/indictrans2-indic-indic-dist-320M
```

Translation direction:

```text
Hindi → Santali
```

Language codes:

```text
Source: hin_Deva
Target: sat_Olck
```

---

## Android Deployment Model

The Android application uses the publicly available INT8 ONNX bundle:

```text
hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8
```

The ONNX bundle contains the required encoder, decoder, cached decoder, vocabularies, tokenizer resources, and configuration files.

---

# Why INT8 ONNX?

The objective was not just to make the translation model work.

The objective was to make it **practical for mobile deployment**.

A standard large transformer deployment can be difficult to use on constrained Android hardware because of:

* Model size
* Memory requirements
* Runtime overhead
* Storage constraints

The current PALASH deployment uses an **INT8 ONNX model** to make local inference more practical.

The current application is approximately:

```text
~775 MB
```

including the bundled model assets and application components.

Despite this footprint, the complete pipeline has been successfully run on a **3 GB RAM Android device**.

This demonstrates that the system can operate locally on hardware considerably more constrained than a typical desktop ML environment.

---

# The Interesting Technical Part: Vocabulary-ID Mapping

One of the most important issues encountered during deployment was the difference between:

```text
SentencePiece Vocabulary IDs
                ≠
IndicTrans2 Model Vocabulary IDs
```

Initially, raw SentencePiece IDs were passed directly to the IndicTrans2 model.

This produced malformed and incorrect output.

The problem was traced to the vocabulary mismatch.

---

## Solution

A custom mapping was created:

```text
SentencePiece ID
       │
       ▼
Custom Mapping
       │
       ▼
IndicTrans2 Model ID
```

The mapping is generated using:

```text
make_mapping.py
```

and stored as:

```text
app/src/main/assets/file_tra/spm_to_model_src.json
```

The mapping generation reported:

```text
SentencePiece vocabulary size: 128000
IndicTrans2 vocabulary size: 122706
Missing pieces: 7820
```

For the tested Hindi inputs, the required pieces were present in the IndicTrans2 vocabulary.

This mapping solved the source-tokenization problem and allowed the Android implementation to produce valid translation output.

---

# Target-Side Decoding

The generated decoder IDs are IndicTrans2 model vocabulary IDs.

Therefore, they cannot simply be decoded using SentencePiece's native vocabulary directly.

The Android implementation instead loads:

```text
dict.TGT.json
```

and uses the IndicTrans2 target vocabulary to convert generated IDs back into target-language pieces.

Special tokens such as:

```text
<s>
</s>
<pad>
```

are skipped.

The resulting target pieces are then reconstructed into normal text and displayed as Santali in the Ol Chiki script.

---

# ONNX Architecture

The Android implementation uses three ONNX sessions:

```text
1. encoder_model.onnx
2. decoder_model.onnx
3. decoder_with_past_model.onnx
```

The first decoder handles the initial generation step.

The cached decoder is then used for subsequent autoregressive generation.

The Android runtime currently uses:

```text
Intra-op threads: 1
Inter-op threads: 1
Optimization: BASIC_OPT
```

This configuration successfully loads and runs the model on the physical Android test device.

---

# Android Architecture

## IndicTranslator.kt

Responsible for:

* Copying translation model assets
* Loading source vocabulary
* Loading target vocabulary
* Loading SentencePiece assets
* Loading the custom vocabulary mapping
* Creating the ONNX Runtime environment
* Creating encoder and decoder sessions
* Tokenizing Hindi text
* Converting token IDs
* Running encoder inference
* Running autoregressive decoding
* Converting model IDs into Santali text

---

## MainActivity.kt

Responsible for:

* Microphone permission
* Vosk model loading
* Speech recognition
* Recognition callbacks
* Hindi transcript state
* Translator initialization
* Passing recognized Hindi text into the translation pipeline
* UI state updates

General flow:

```text
startVosk()
     ↓
SpeechService
     ↓
Speech Recognition
     ↓
Hindi Transcript
     ↓
IndicTranslator
     ↓
Santali Translation
     ↓
Ol Chiki Output
```

Translation inference runs away from the main UI thread.

---

# Real Device Testing

The application was tested on a physical Android phone.

### Test Device

```text
OnePlus CPH2467
Android 15
API 35
```

The following were verified on the real device:

* Application builds
* Application installs
* Vosk model loads
* ONNX model loads
* Offline speech recognition works
* Hindi text is passed into the translation model
* IndicTrans2 produces Santali output
* Santali output is displayed in Ol Chiki
* PC and Android outputs matched for tested examples

---

# Performance

The current implementation achieved approximately:

```text
~0.725 seconds
```

for translation inference in testing.

This measurement was taken after testing the selected model pipeline on the target Android deployment.

The reported figure represents the tested inference setup and is not intended as a universal latency guarantee across all devices, sentence lengths, or model configurations.

The broader model-selection process involved evaluating multiple pretrained approaches before arriving at the current deployment pipeline.

Future benchmarking can evaluate:

* Short sentences
* Medium sentences
* Long sentences
* Multiple repeated runs
* Average latency
* Cold-start latency
* Warm inference latency
* Memory usage

---

# End-to-End System

The current system can be summarized as:

```text
        PALASH TRANSLATOR

             Microphone
                  │
                  ▼
            Vosk Hindi ASR
                  │
                  ▼
              Hindi Text
                  │
                  ▼
        SentencePiece Tokenization
                  │
                  ▼
         SPM → Model-ID Mapping
                  │
                  ▼
          IndicTrans2 INT8
             ONNX Runtime
                  │
                  ▼
        Autoregressive Decoding
                  │
                  ▼
           Target Vocabulary
                  │
                  ▼
          Santali / Ol Chiki
                  │
                  ▼
              Android UI
```

---

# Current Status

| Component                   | Status                           |
| --------------------------- | -------------------------------- |
| Hindi Vosk ASR              | ✅ Working                        |
| Model research / selection  | ✅ Completed for current pipeline |
| IndicTrans2 inference       | ✅ Working                        |
| INT8 ONNX model             | ✅ Working                        |
| ONNX Runtime Android        | ✅ Working                        |
| SentencePiece integration   | ✅ Working                        |
| Custom vocabulary mapping   | ✅ Working                        |
| Hindi → Santali             | ✅ Working                        |
| Santali / Ol Chiki output   | ✅ Working                        |
| PC ↔ Android parity testing | ✅ Tested                         |
| Physical Android deployment | ✅ Working                        |
| Offline inference           | ✅ Working                        |
| 3 GB RAM device testing     | ✅ Working                        |
| Jetpack Compose UI          | ✅ Complete                       |

---

# Why This Project Matters

The interesting part of PALASH Translator is not simply that it translates text.

It explores the engineering challenge of taking modern pretrained AI models and making them usable in an environment where:

```text
Internet may be unavailable
        +
Hardware resources are limited
        +
Cloud APIs cannot be relied upon
        ↓
Run the AI locally
```

The project therefore focuses on the complete journey:

```text
Model Research
      ↓
Model Evaluation
      ↓
Model Selection
      ↓
Model Optimization
      ↓
ONNX Deployment
      ↓
Tokenizer Integration
      ↓
Android Integration
      ↓
Real Device Testing
```

---

# Limitations

* The underlying ASR and translation models are pretrained third-party models.
* This project does not claim to have trained IndicTrans2 or Vosk from scratch.
* Translation quality has been validated on tested examples rather than through a large independent benchmark in this repository.
* The current application footprint is approximately 775 MB.

---

# Future Work

Potential future improvements include:

* Larger-scale translation-quality evaluation
* More systematic latency benchmarking
* Cold-start vs warm-start profiling
* Memory profiling
* Improved error handling
* Code cleanup and refactoring
* Further mobile inference optimization
* Santali to Hindi translation using ASR

---

# Third-Party Models & Attribution

PALASH Translator integrates publicly available pretrained models and open-source software.

The underlying model weights were **not trained from scratch as part of this project**.

## Vosk

Model:

```text
vosk-model-small-hi-0.22
```

Project:

[https://alphacephei.com/vosk/](https://alphacephei.com/vosk/)

---

## IndicTrans2

Base model:

```text
ai4bharat/indictrans2-indic-indic-dist-320M
```

Project:

[https://github.com/AI4Bharat/IndicTrans2](https://github.com/AI4Bharat/IndicTrans2)

---

## INT8 ONNX Model

Model bundle used for Android deployment:

```text
hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8
```

Model repository:

[https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8](https://huggingface.co/hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8)

---

## Libraries

The project also uses:

* Vosk Android
* ONNX Runtime Android
* SentencePiece
* Jetpack Compose
* Material 3

Please refer to the respective upstream repositories, package metadata, and model cards for the applicable licenses and attribution requirements.

This repository does not claim ownership of third-party pretrained model weights.

---

# Installation

## Requirements

* Android Studio
* JDK 11
* Android SDK
* Android device or emulator
* Git
* Git LFS

## Clone

```bash
git clone https://github.com/gauravtiwari0259/palash_translator.git
cd palash_translator
```

Because the repository contains large model files managed using Git LFS:

```bash
git lfs install
git lfs pull
```

Then open the project in Android Studio and build the application.

---

# Project Structure

```text
palash_translator/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── file_tra/
│   │   │   │   └── model-hi/
│   │   │   │
│   │   │   ├── java/
│   │   │   │   └── com/example/palashtranslator/
│   │   │   │       ├── MainActivity.kt
│   │   │   │       ├── IndicTranslator.kt
│   │   │   │       └── ui/
│   │   │   │
│   │   │   └── res/
│   │   │
│   │   ├── androidTest/
│   │   └── test/
│   │
│   └── build.gradle.kts
│
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── make_mapping.py
├── settings.gradle.kts
├── .gitattributes
└── .gitignore
```

---
