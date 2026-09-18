package com.example

/**
 * Pinned manifest and metadata for candidate on-device language models.
 * Model: Qwen3-1.7B LiteRT-LM INT4 bundle.
 */
data class ModelManifest(
    val modelId: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val revision: String,
    val expectedSizeBytes: Long,
    val expectedSha256: String,
    val contextWindowTokens: Int,
    val license: String,
    val referenceUrl: String,
    val minFreeStorageBytes: Long
) {
    companion object {
        /**
         * Pinned immutable commit for Qwen3-1.7B dynamic wi4b32 afp32 litertlm bundle.
         * Repository: litert-community/Qwen3-1.7B
         * License: Apache 2.0
         */
        val QWEN3_1_7B = ModelManifest(
            modelId = "qwen3-1.7b-litertlm",
            displayName = "Offline AI — Qwen3 1.7B",
            fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/73fbc3fe8271c162a603ee66f6e7ed25b6211195/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            revision = "73fbc3fe8271c162a603ee66f6e7ed25b6211195",
            expectedSizeBytes = 977184032L, // ~931.9 MB
            expectedSha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
            contextWindowTokens = 4096,
            license = "Apache 2.0",
            referenceUrl = "https://huggingface.co/litert-community/Qwen3-1.7B",
            minFreeStorageBytes = 1610612736L // 1.5 GB recommended free space
        )
    }
}
