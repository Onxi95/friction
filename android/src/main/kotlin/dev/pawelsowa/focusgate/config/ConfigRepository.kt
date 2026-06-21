package dev.pawelsowa.focusgate.config

interface ConfigRepository {
    fun read(): AppConfig

    fun write(transform: (AppConfig) -> AppConfig): AppConfig
}
