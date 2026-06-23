package dev.pawelsowa.focusgate.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import dev.pawelsowa.focusgate.data.proto.StoredAppConfig
import dev.pawelsowa.focusgate.data.proto.StoredLockMode
import java.io.InputStream
import java.io.OutputStream

object FocusGateConfigSerializer : Serializer<StoredAppConfig> {
    override val defaultValue: StoredAppConfig =
        StoredAppConfig.newBuilder()
            .setLockState(
                dev.pawelsowa.focusgate.data.proto.StoredLockState.newBuilder()
                    .setMode(StoredLockMode.STORED_LOCK_MODE_UNLOCKED),
            )
            .build()

    override suspend fun readFrom(input: InputStream): StoredAppConfig =
        try {
            StoredAppConfig.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read FocusGate configuration", exception)
        }

    override suspend fun writeTo(t: StoredAppConfig, output: OutputStream) {
        t.writeTo(output)
    }
}
