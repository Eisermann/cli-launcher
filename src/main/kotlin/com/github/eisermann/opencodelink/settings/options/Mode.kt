package com.github.eisermann.opencodelink.settings.options

/**
 * Launch mode for `opencode`.
 */
enum class Mode {
    /** Do not pass any mode-related arguments. */
    DEFAULT,

    /** Pass --full-auto. */
    FULL_AUTO;

    val argument: String
        get() = when (this) {
            DEFAULT -> ""
            FULL_AUTO -> "--full-auto"
        }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default (No arguments)"
        FULL_AUTO -> "Full Auto (--full-auto)"
    }
}
