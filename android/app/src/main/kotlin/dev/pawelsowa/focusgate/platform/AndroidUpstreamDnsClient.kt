package dev.pawelsowa.focusgate.platform

import android.net.VpnService
import dev.pawelsowa.focusgate.vpn.UpstreamDnsRequest
import dev.pawelsowa.focusgate.vpn.UpstreamDnsResult
import dev.pawelsowa.focusgate.vpn.UpstreamDnsTransport
import dev.pawelsowa.focusgate.vpn.VpnSocketProtector
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class AndroidVpnSocketProtector(
    private val vpnService: VpnService,
) : VpnSocketProtector {
    override fun protect(
        host: String,
        port: Int,
    ): Boolean {
        val socket = DatagramSocket()
        val protected = vpnService.protect(socket)
        socket.close()
        return protected
    }
}

class AndroidUpstreamDnsTransport(
    private val vpnService: VpnService,
) : UpstreamDnsTransport {
    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult {
        DatagramSocket().use { socket ->
            require(vpnService.protect(socket)) { "COULD_NOT_PROTECT_SOCKET" }
            socket.soTimeout = 5_000
            val address = InetAddress.getByName(request.host)
            val packet = DatagramPacket(request.packet, request.packet.size, address, request.port)
            socket.send(packet)
            val response = ByteArray(4_096)
            val responsePacket = DatagramPacket(response, response.size)
            socket.receive(responsePacket)
            return UpstreamDnsResult(response.copyOf(responsePacket.length))
        }
    }
}

class AndroidTcpUpstreamDnsTransport(
    private val vpnService: VpnService,
) : UpstreamDnsTransport {
    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult {
        Socket(request.host, request.port).use { socket ->
            require(vpnService.protect(socket)) { "COULD_NOT_PROTECT_SOCKET" }
            socket.soTimeout = 5_000
            socket.getOutputStream().write(request.packet)
            socket.getOutputStream().flush()

            val header = socket.getInputStream().readNBytes(2)
            require(header.size == 2) { "MALFORMED_DNS_TCP_PACKET" }
            val expectedLength = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
            val body = socket.getInputStream().readNBytes(expectedLength)
            require(body.size == expectedLength) { "MALFORMED_DNS_TCP_PACKET" }
            return UpstreamDnsResult(header + body)
        }
    }
}
