package dev.pawelsowa.focusgate.vpn

import dev.pawelsowa.focusgate.domain.model.DomainRule
import dev.pawelsowa.focusgate.domain.model.MatchMode
import dev.pawelsowa.focusgate.domain.model.ScheduleMode
import dev.pawelsowa.focusgate.domain.model.WeeklySchedule
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DnsPacketProcessorTest {
    private val codec = Ipv4UdpPacketCodec()
    private val mondayTen = ZonedDateTime.parse("2026-06-22T10:00:00Z")

    @Before
    fun resetRuntime() {
        VpnRuntime.failureReason.value = VpnFailureReason.NONE
    }

    @Test
    fun blockedRuleReturnsNxdomainWithoutUpstreamQuery() {
        var upstreamCalled = false
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient {
                upstreamCalled = true
                null
            },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = udpDnsRequest(dnsQuery("www.facebook.com")),
            length = udpDnsRequest(dnsQuery("www.facebook.com")).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        assertNotNull(response)
        val decoded = codec.decode(response ?: ByteArray(0))
        assertNotNull(decoded)
        assertEquals(53, decoded?.sourcePort)
        assertEquals(45_000, decoded?.destinationPort)
        val flags = DnsMessageParser.readUnsignedShort(decoded?.payload ?: ByteArray(0), 2)
        assertEquals(0x8000, flags and 0x8000)
        assertEquals(3, flags and 0x000f)
        assertEquals(false, upstreamCalled)
    }

    @Test
    fun subdomainBlockedRuleReturnsNxdomain() {
        val request = udpDnsRequest(dnsQuery("m.facebook.com"))
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { null },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = request,
            length = request.size,
            rules = listOf(blockingRule("facebook.com")),
        )

        val decoded = codec.decode(response ?: ByteArray(0))
        val flags = DnsMessageParser.readUnsignedShort(decoded?.payload ?: ByteArray(0), 2)
        assertEquals(3, flags and 0x000f)
    }

    @Test
    fun allowedRuleForwardsUpstreamResponse() {
        val query = dnsQuery("example.com")
        val upstreamResponse = query.copyOf().also { DnsMessageParser.writeUnsignedShort(it, 2, 0x8180) }
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { request ->
                assertArrayEquals(query, request)
                upstreamResponse
            },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        val decoded = codec.decode(response ?: ByteArray(0))
        assertArrayEquals(upstreamResponse, decoded?.payload)
    }

    @Test
    fun scheduleBoundaryChangesFilteringWithoutRestartingProcessor() {
        var now = mondayTen
        val query = dnsQuery("facebook.com")
        val upstreamResponse = query.copyOf().also { DnsMessageParser.writeUnsignedShort(it, 2, 0x8180) }
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { upstreamResponse },
            now = { now },
        )

        val blockedResponse = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )
        now = mondayTen.plusHours(1)
        val allowedResponse = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        val blockedPayload = codec.decode(blockedResponse ?: ByteArray(0))?.payload ?: ByteArray(0)
        val allowedPayload = codec.decode(allowedResponse ?: ByteArray(0))?.payload ?: ByteArray(0)
        assertEquals(3, DnsMessageParser.readUnsignedShort(blockedPayload, 2) and 0x000f)
        assertArrayEquals(upstreamResponse, allowedPayload)
    }

    @Test
    fun allowedUdpUpstreamFailureIsReported() {
        val query = dnsQuery("example.com")
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { null },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        assertNull(response)
        assertEquals(VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE, VpnRuntime.failureReason.value)
    }

    @Test
    fun upstreamFailureClearsAfterSuccessfulForward() {
        val query = dnsQuery("example.com")
        val upstreamResponse = query.copyOf().also { DnsMessageParser.writeUnsignedShort(it, 2, 0x8180) }
        VpnRuntime.failureReason.value = VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { upstreamResponse },
            now = { mondayTen },
        )

        processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        assertEquals(VpnFailureReason.NONE, VpnRuntime.failureReason.value)
    }

    @Test
    fun multipleQuestionQueryBlocksWhenAnySupportedQuestionMatches() {
        var upstreamCalled = false
        val query = dnsQuery("example.com", "m.facebook.com")
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient {
                upstreamCalled = true
                null
            },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        val decoded = codec.decode(response ?: ByteArray(0))
        val flags = DnsMessageParser.readUnsignedShort(decoded?.payload ?: ByteArray(0), 2)
        assertEquals(2, DnsMessageParser.readUnsignedShort(decoded?.payload ?: ByteArray(0), 4))
        assertEquals(3, flags and 0x000f)
        assertEquals(false, upstreamCalled)
    }

    @Test
    fun allowedQueryWithEdnsForwardsOriginalMessage() {
        val query = dnsQueryWithEdns("example.com")
        val upstreamResponse = query.copyOf().also { DnsMessageParser.writeUnsignedShort(it, 2, 0x8180) }
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { request ->
                assertArrayEquals(query, request)
                upstreamResponse
            },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        val decoded = codec.decode(response ?: ByteArray(0))
        assertArrayEquals(upstreamResponse, decoded?.payload)
    }

    @Test
    fun allowedTruncatedResponseIsForwarded() {
        val query = dnsQuery("example.com")
        val upstreamResponse = query.copyOf().also { DnsMessageParser.writeUnsignedShort(it, 2, 0x8380) }
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { upstreamResponse },
            now = { mondayTen },
        )

        val response = processor.process(
            packetBytes = udpDnsRequest(query),
            length = udpDnsRequest(query).size,
            rules = listOf(blockingRule("facebook.com")),
        )

        val decoded = codec.decode(response ?: ByteArray(0))
        assertArrayEquals(upstreamResponse, decoded?.payload)
    }

    @Test
    fun ipv6DnsPacketsCanBeBlocked() {
        val request = ipv6UdpDnsRequest(dnsQuery("facebook.com"))
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { null },
            now = { mondayTen },
        )

        val response = processor.process(request, request.size, listOf(blockingRule("facebook.com")))

        val decoded = Ipv6UdpPacketCodec().decode(response ?: ByteArray(0))
        assertEquals(53, decoded?.sourcePort)
        val flags = DnsMessageParser.readUnsignedShort(decoded?.payload ?: ByteArray(0), 2)
        assertEquals(3, flags and 0x000f)
    }

    @Test
    fun tcpDnsFrameCanBeBlocked() {
        val query = dnsQuery("facebook.com")
        val frame = ByteArray(2 + query.size)
        DnsMessageParser.writeUnsignedShort(frame, 0, query.size)
        query.copyInto(frame, 2)
        val processor = DnsTcpMessageProcessor(
            upstream = UpstreamDnsClient { null },
            now = { mondayTen },
        )

        val response = processor.process(frame, listOf(blockingRule("facebook.com")))

        assertNotNull(response)
        assertEquals((response?.size ?: 0) - 2, DnsMessageParser.readUnsignedShort(response ?: ByteArray(0), 0))
        val flags = DnsMessageParser.readUnsignedShort(response ?: ByteArray(0), 4)
        assertEquals(3, flags and 0x000f)
    }

    @Test
    fun ipv4TcpDnsPacketCanBeBlocked() {
        val query = dnsQuery("facebook.com")
        val frame = tcpDnsFrame(query)
        val request = tcpDnsRequest(frame)
        var tcpUpstreamCalled = false
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { ByteArray(0) },
            tcpUpstream = UpstreamDnsClient {
                tcpUpstreamCalled = true
                null
            },
            now = { mondayTen },
        )

        val response = processor.process(request, request.size, listOf(blockingRule("facebook.com")))

        val decoded = Ipv4TcpPacketCodec().decode(response ?: ByteArray(0))
        assertEquals(53, decoded?.sourcePort)
        assertEquals(45_000, decoded?.destinationPort)
        val payload = decoded?.payload ?: ByteArray(0)
        assertEquals(payload.size - 2, DnsMessageParser.readUnsignedShort(payload, 0))
        val flags = DnsMessageParser.readUnsignedShort(payload, 4)
        assertEquals(3, flags and 0x000f)
        assertEquals(false, tcpUpstreamCalled)
    }

    @Test
    fun ipv4TcpDnsPacketForwardsAllowedQueryToTcpUpstream() {
        val query = dnsQuery("example.com")
        val upstreamResponse = query.copyOf().also { DnsMessageParser.writeUnsignedShort(it, 2, 0x8180) }
        val request = tcpDnsRequest(tcpDnsFrame(query))
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { ByteArray(0) },
            tcpUpstream = UpstreamDnsClient { upstreamRequest ->
                assertArrayEquals(query, upstreamRequest)
                upstreamResponse
            },
            now = { mondayTen },
        )

        val response = processor.process(request, request.size, listOf(blockingRule("facebook.com")))

        val decoded = Ipv4TcpPacketCodec().decode(response ?: ByteArray(0))
        val payload = decoded?.payload ?: ByteArray(0)
        assertEquals(upstreamResponse.size, DnsMessageParser.readUnsignedShort(payload, 0))
        assertArrayEquals(upstreamResponse, payload.copyOfRange(2, payload.size))
    }

    @Test
    fun allowedTcpUpstreamFailureIsReported() {
        val query = dnsQuery("example.com")
        val request = tcpDnsRequest(tcpDnsFrame(query))
        val processor = DnsPacketProcessor(
            upstream = UpstreamDnsClient { ByteArray(0) },
            tcpUpstream = UpstreamDnsClient { null },
            now = { mondayTen },
        )

        val response = processor.process(request, request.size, listOf(blockingRule("facebook.com")))

        assertNull(response)
        assertEquals(VpnFailureReason.UPSTREAM_DNS_UNAVAILABLE, VpnRuntime.failureReason.value)
    }

    @Test
    fun nonDnsPacketsAreIgnored() {
        val request = udpDnsRequest(dnsQuery("example.com"), destinationPort = 443)
        val processor = DnsPacketProcessor(upstream = UpstreamDnsClient { ByteArray(0) })

        assertNull(processor.process(request, request.size, emptyList()))
    }

    @Test
    fun malformedDnsPacketsAreIgnored() {
        val malformed = dnsQuery("example.com").copyOf(16)
        val request = udpDnsRequest(malformed)
        val processor = DnsPacketProcessor(upstream = UpstreamDnsClient { ByteArray(0) })

        assertNull(processor.process(request, request.size, emptyList()))
    }

    @Test
    fun oversizedUdpDnsPacketsAreIgnored() {
        val oversized = dnsQuery("example.com").copyOf(4_097)
        val request = udpDnsRequest(oversized)
        val processor = DnsPacketProcessor(upstream = UpstreamDnsClient { ByteArray(0) })

        assertNull(processor.process(request, request.size, emptyList()))
    }

    @Test
    fun oversizedTcpDnsFramesAreIgnored() {
        val message = dnsQuery("example.com").copyOf(4_097)
        val frame = ByteArray(2 + message.size)
        DnsMessageParser.writeUnsignedShort(frame, 0, message.size)
        message.copyInto(frame, 2)
        val processor = DnsTcpMessageProcessor(upstream = UpstreamDnsClient { ByteArray(0) })

        assertNull(processor.process(frame, listOf(blockingRule("facebook.com"))))
    }

    private fun blockingRule(domain: String): DomainRule =
        DomainRule(
            id = domain,
            domain = domain,
            enabled = true,
            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
            schedule = WeeklySchedule.empty().withSlot(dayIndex = 0, hour = 10, selected = true),
        )

    private fun dnsQuery(vararg domains: String): ByteArray {
        val size = 12 + domains.sumOf { domain ->
            domain.split(".").sumOf { label -> label.length + 1 } + 1 + 4
        }
        val message = ByteArray(size)
        DnsMessageParser.writeUnsignedShort(message, 0, 0x1234)
        DnsMessageParser.writeUnsignedShort(message, 2, 0x0100)
        DnsMessageParser.writeUnsignedShort(message, 4, domains.size)
        var offset = 12
        domains.forEach { domain ->
            offset = writeQuestion(message, offset, domain)
        }
        return message
    }

    private fun dnsQueryWithEdns(domain: String): ByteArray {
        val query = dnsQuery(domain)
        val message = query.copyOf(query.size + 11)
        DnsMessageParser.writeUnsignedShort(message, 10, 1)
        var offset = query.size
        message[offset] = 0
        offset += 1
        DnsMessageParser.writeUnsignedShort(message, offset, 41)
        DnsMessageParser.writeUnsignedShort(message, offset + 2, 1_232)
        return message
    }

    private fun writeQuestion(message: ByteArray, startOffset: Int, domain: String): Int {
        var offset = startOffset
        domain.split(".").forEach { label ->
            message[offset] = label.length.toByte()
            offset += 1
            label.encodeToByteArray().copyInto(message, offset)
            offset += label.length
        }
        offset += 1
        DnsMessageParser.writeUnsignedShort(message, offset, DnsMessageParser.TYPE_A)
        DnsMessageParser.writeUnsignedShort(message, offset + 2, 1)
        offset += 4
        return offset
    }

    private fun udpDnsRequest(payload: ByteArray, destinationPort: Int = 53): ByteArray {
        val packet = ByteArray(20 + 8 + payload.size)
        packet[0] = 0x45.toByte()
        DnsMessageParser.writeUnsignedShort(packet, 2, packet.size)
        DnsMessageParser.writeUnsignedShort(packet, 4, 7)
        packet[8] = 64
        packet[9] = 17
        byteArrayOf(10, 0, 0, 2).copyInto(packet, 12)
        byteArrayOf(10, 10, 0, 2).copyInto(packet, 16)
        DnsMessageParser.writeUnsignedShort(packet, 20, 45_000)
        DnsMessageParser.writeUnsignedShort(packet, 22, destinationPort)
        DnsMessageParser.writeUnsignedShort(packet, 24, 8 + payload.size)
        payload.copyInto(packet, 28)
        return packet
    }

    private fun tcpDnsFrame(payload: ByteArray): ByteArray {
        val frame = ByteArray(2 + payload.size)
        DnsMessageParser.writeUnsignedShort(frame, 0, payload.size)
        payload.copyInto(frame, 2)
        return frame
    }

    private fun tcpDnsRequest(payload: ByteArray, destinationPort: Int = 53): ByteArray {
        val packet = ByteArray(20 + 20 + payload.size)
        packet[0] = 0x45.toByte()
        DnsMessageParser.writeUnsignedShort(packet, 2, packet.size)
        DnsMessageParser.writeUnsignedShort(packet, 4, 8)
        packet[8] = 64
        packet[9] = 6
        byteArrayOf(10, 0, 0, 2).copyInto(packet, 12)
        byteArrayOf(10, 10, 0, 2).copyInto(packet, 16)
        DnsMessageParser.writeUnsignedShort(packet, 20, 45_000)
        DnsMessageParser.writeUnsignedShort(packet, 22, destinationPort)
        writeUnsignedInt(packet, 24, 1_000)
        writeUnsignedInt(packet, 28, 9_000)
        packet[32] = 0x50
        packet[33] = 0x18
        DnsMessageParser.writeUnsignedShort(packet, 34, 65_535)
        payload.copyInto(packet, 40)
        return packet
    }

    private fun writeUnsignedInt(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value shr 24).toByte()
        bytes[offset + 1] = (value shr 16).toByte()
        bytes[offset + 2] = (value shr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun ipv6UdpDnsRequest(payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val packet = ByteArray(40 + udpLength)
        packet[0] = 0x60.toByte()
        DnsMessageParser.writeUnsignedShort(packet, 4, udpLength)
        packet[6] = 17
        packet[7] = 64
        byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1,
        ).copyInto(packet, 8)
        byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 2,
        ).copyInto(packet, 24)
        DnsMessageParser.writeUnsignedShort(packet, 40, 45_000)
        DnsMessageParser.writeUnsignedShort(packet, 42, 53)
        DnsMessageParser.writeUnsignedShort(packet, 44, udpLength)
        payload.copyInto(packet, 48)
        return packet
    }
}
