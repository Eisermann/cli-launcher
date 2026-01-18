package com.github.eisermann.opencodelink.settings.options

/**
 * Model selection for the `--model` argument.
 */
enum class Model {
    /** Do not pass --model. */
    DEFAULT,

    // GPT 5.2 models
    GPT_5_2_CODEX,
    GPT_5_2,

    // GPT 5.1 models
    GPT_5_1,
    GPT_5_1_CODEX,
    GPT_5_1_CODEX_MAX,
    GPT_5_1_CODEX_MINI,

    // Legacy
    GPT_5,
    GPT_5_CODEX,
    CODEX_MINI_LATEST,

    /** Use customModel from settings. */
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        GPT_5_2_CODEX -> "gpt-5.2-codex"
        GPT_5_2 -> "gpt-5.2"
        GPT_5_1 -> "gpt-5.1"
        GPT_5_1_CODEX -> "gpt-5.1-codex"
        GPT_5_1_CODEX_MAX -> "gpt-5.1-codex-max"
        GPT_5_1_CODEX_MINI -> "gpt-5.1-codex-mini"
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"
        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default (use CLI default)"
        GPT_5_2_CODEX -> "GPT 5.2 Codex"
        GPT_5_2 -> "GPT 5.2"
        GPT_5_1 -> "GPT 5.1"
        GPT_5_1_CODEX -> "GPT 5.1 Codex"
        GPT_5_1_CODEX_MAX -> "GPT 5.1 Codex Max"
        GPT_5_1_CODEX_MINI -> "GPT 5.1 Codex Mini"
        GPT_5 -> "GPT 5"
        GPT_5_CODEX -> "GPT 5 Codex"
        CODEX_MINI_LATEST -> "Codex Mini (Latest)"
        CUSTOM -> "Custom..."
    }

    val supportsReasoningEffort: Boolean
        get() = false

    override fun toString(): String = toDisplayName()
}
