package dev.pawelsowa.focusgate.platform

import android.os.ParcelFileDescriptor
import dev.pawelsowa.focusgate.config.ConfigRepository
import dev.pawelsowa.focusgate.dns.TunDropReason
import dev.pawelsowa.focusgate.dns.TunLoopStats
import dev.pawelsowa.focusgate.dns.TunPacketHandler
import dev.pawelsowa.focusgate.dns.TunPacketResult
import dev.pawelsowa.focusgate.vpn.FocusGateVpnService
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.ZonedDateTime

class TunDnsLoop(
    private val tunInterface: ParcelFileDescriptor,
    private val repository: ConfigRepository,
    private val vpnService: FocusGateVpnService,
) : AutoCloseable {
    @Volatile
    private var running = true

    private val input = FileInputStream(tunInterface.fileDescriptor)
    private val output = FileOutputStream(tunInterface.fileDescriptor)
    private val packetHandler = TunPacketHandler(vpnService)

    @Volatile
    var stats: TunLoopStats = TunLoopStats()
        private set

    fun run() {
        val buffer = ByteArray(MAX_PACKET_SIZE)

        while (running) {
            val length = input.read(buffer)
            if (length <= 0) {
                continue
            }

            val packet = buffer.copyOf(length)
            runCatching { handlePacket(packet) }.onSuccess { result ->
                when (result) {
                    is TunPacketResult.Respond -> {
                        output.write(result.packet)
                        stats = stats.copy(respondedPackets = stats.respondedPackets + 1)
                    }
                    is TunPacketResult.Drop -> {
                        stats =
                            stats.copy(
                                droppedPackets = stats.droppedPackets + 1,
                                lastDropReason = result.reason,
                            )
                    }
                }
            }
        }
    }

    override fun close() {
        running = false
        input.close()
        output.close()
        tunInterface.close()
    }

    internal fun handlePacket(packet: ByteArray): TunPacketResult {
        val config = repository.read()
        return packetHandler.handle(packet, ZonedDateTime.now(), config)
    }

    companion object {
        private const val MAX_PACKET_SIZE = 32_767
    }
}
