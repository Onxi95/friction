package dev.pawelsowa.focusgate.platform

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.pawelsowa.focusgate.proto.AppConfig
import java.io.InputStream
import java.io.OutputStream

object AppConfigSerializer : Serializer<AppConfig> {
    override val defaultValue: AppConfig = AppConfig.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppConfig =
        try {
            AppConfig.parseFrom(input)
        } catch (exception: Exception) {
            throw CorruptionException("Cannot read proto.", exception)
        }

    override suspend fun writeTo(
        t: AppConfig,
        output: OutputStream,
    ) {
        t.writeTo(output)
    }
}
