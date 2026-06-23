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
import org.junit.Test

class DnsPacketProcessorTest {
    private val codec = Ipv4UdpPacketCodec()
    private val mondayTen = ZonedDateTime.parse("2026-06-22T10:00:00Z")

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
    fun nonDnsPacketsAreIgnored() {
        val request = udpDnsRequest(dnsQuery("example.com"), destinationPort = 443)
        val processor = DnsPacketProcessor(upstream = UpstreamDnsClient { ByteArray(0) })

        assertNull(processor.process(request, request.size, emptyList()))
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

    private fun dnsQuery(domain: String): ByteArray {
        val labels = domain.split(".")
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val message = ByteArray(size)
        DnsMessageParser.writeUnsignedShort(message, 0, 0x1234)
        DnsMessageParser.writeUnsignedShort(message, 2, 0x0100)
        DnsMessageParser.writeUnsignedShort(message, 4, 1)
        var offset = 12
        labels.forEach { label ->
            message[offset] = label.length.toByte()
            offset += 1
            label.encodeToByteArray().copyInto(message, offset)
            offset += label.length
        }
        offset += 1
        DnsMessageParser.writeUnsignedShort(message, offset, DnsMessageParser.TYPE_A)
        DnsMessageParser.writeUnsignedShort(message, offset + 2, 1)
        return message
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
