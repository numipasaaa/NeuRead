package com.psimandan.neuread.voice

class LlamaEngine {

    // Load the C++ library when this class is initialized
    companion object {
        init {
            System.loadLibrary("llama-bridge")
        }
    }

    // 1. Initialize the LLM with the path to the .gguf file in your assets
    external fun initModel(modelPath: String): Boolean

    // 2. Pass the text prompt and receive an array of token IDs
    external fun generateSpeechTokens(prompt: String): IntArray

    // 3. Free the C++ memory when done
    external fun freeModel()
}