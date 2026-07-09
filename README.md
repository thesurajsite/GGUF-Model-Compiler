# Gguf Model Runner

Gguf Model Runner is a lightweight Android application designed to run Large Language Models (LLMs) locally on your Android device. It uses the GGUF (GPT-Generated Unified Format) and leverages the power of `llama.cpp` through a JNI bridge for efficient on-device inference.

## Features
- **Local Inference:** Run LLMs without an internet connection, ensuring privacy and low latency.
- **GGUF Support:** Compatible with any model converted to the `.gguf` format.
- **Optimized Performance:** Uses a dedicated native thread for inference to keep the UI responsive.
- **ChatML Ready:** Pre-configured with ChatML prompting, making it ideal for models like Qwen.

## Setup Instructions

### 1. Add your Model
To run a model, you need to provide a `.gguf` file:
1. Navigate to `app/src/main/assets/`.
2. Place your `.gguf` model file inside this folder.
3. By default, the app expects the file to be named `model.gguf`.

### 2. (Optional) Change Model Filename
If you want to use a different filename for your model:
1. Open `app/src/main/java/com/suraj/ggufmodelrunner/data/ChatRepository.kt`.
2. Locate the `initModel` function.
3. Change the `assetName` default value from `"model.gguf"` to your filename.

```kotlin
suspend fun initModel(
    assetName: String = "your_model_name.gguf", // Change here
    nCtx: Int = 2048,
    nThreads: Int = 4
)
```

### 3. Build and Run
1. Open the project in Android Studio.
2. Connect your Android device.
3. Click **Run**.
4. The app will copy the model from assets to internal storage on the first run (this might take a moment depending on the model size) and then initialize the chat interface.

## Technical Details
- **Backend:** `llama.cpp` compiled for Android.
- **Bridge:** `LlamaBridge.kt` handles the communication between Kotlin and the native C++ code.
- **Concurrency:** Uses a custom `CoroutineDispatcher` with a single-threaded executor to ensure thread safety for native calls.

## Note
- Ensure your device has enough RAM to load the model you choose.
- Only `.gguf` files are supported.
