package dev.pawelsowa.focusgate

import dev.pawelsowa.focusgate.config.FileConfigRepository
import dev.pawelsowa.focusgate.dns.DnsAction
import dev.pawelsowa.focusgate.dns.DnsMessageCodec
import dev.pawelsowa.focusgate.dns.DnsTcpCodec
import dev.pawelsowa.focusgate.dns.DnsPacketProcessor
import dev.pawelsowa.focusgate.dns.Ipv4UdpPacketCodec
import dev.pawelsowa.focusgate.dns.Ipv6UdpPacketCodec
import dev.pawelsowa.focusgate.dns.PacketProcessingResult
import dev.pawelsowa.focusgate.dns.DnsPolicyEvaluator
import dev.pawelsowa.focusgate.dns.TunDropReason
import dev.pawelsowa.focusgate.dns.TunPacketHandler
import dev.pawelsowa.focusgate.dns.TunPacketResult
import dev.pawelsowa.focusgate.domain.DomainMatcher
import dev.pawelsowa.focusgate.domain.DomainNormalizer
import dev.pawelsowa.focusgate.domain.DomainRule
import dev.pawelsowa.focusgate.domain.MatchMode
import dev.pawelsowa.focusgate.domain.RuleEvaluator
import dev.pawelsowa.focusgate.domain.ScheduleMode
import dev.pawelsowa.focusgate.env.MutableDeviceContext
import dev.pawelsowa.focusgate.lock.EditLockState
import dev.pawelsowa.focusgate.lock.LockManager
import dev.pawelsowa.focusgate.native.DomainRuleDto
import dev.pawelsowa.focusgate.native.FocusGateBridge
import dev.pawelsowa.focusgate.native.AppConfigDto
import dev.pawelsowa.focusgate.native.UpstreamDnsDto
import dev.pawelsowa.focusgate.native.VpnConfigDto
import dev.pawelsowa.focusgate.vpn.FocusGateVpnService
import dev.pawelsowa.focusgate.vpn.UpstreamDnsClient
import dev.pawelsowa.focusgate.vpn.UpstreamDnsRequest
import dev.pawelsowa.focusgate.vpn.UpstreamDnsResult
import dev.pawelsowa.focusgate.vpn.UpstreamDnsTransport
import dev.pawelsowa.focusgate.vpn.VpnSocketProtector
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun main() {
    testDomainNormalization()
    testDomainMatching()
    testScheduleEvaluation()
    testLockManager()
    testConfigRepositoryPersistence()
    testDnsPolicyEvaluator()
    testDnsMessageCodec()
    testDnsTcpCodec()
    testDnsPacketProcessor()
    testIpv4UdpPacketCodec()
    testIpv6UdpPacketCodec()
    testTunPacketHandler()
    testUpstreamDnsClientProtection()
    testUpstreamDnsClientRetries()
    testVpnServiceDnsHandling()
    testVpnReporting()
    testVpnRuntimeController()
    testNativeBridgeLockAndPersistence()
    println("Kotlin tests passed")
}

private fun testDomainNormalization() {
    val normalizer = DomainNormalizer()
    assertEquals("facebook.com", normalizer.normalize(" Facebook.com. "))
    assertFails("INVALID_DOMAIN") { normalizer.normalize("https://facebook.com") }
    assertFails("INVALID_DOMAIN") { normalizer.normalize("facebook.com/feed") }
}

private fun testDomainMatching() {
    assertTrue(DomainMatcher.matchesExact("facebook.com", "facebook.com"))
    assertFalse(DomainMatcher.matchesExact("www.facebook.com", "facebook.com"))
    assertTrue(DomainMatcher.matchesDomainAndSubdomains("www.facebook.com", "facebook.com"))
    assertFalse(DomainMatcher.matchesDomainAndSubdomains("notfacebook.com", "facebook.com"))
}

private fun testScheduleEvaluation() {
    val slots = BooleanArray(168)
    slots[RuleEvaluator.getSlotIndex(0, 19)] = true

    val allowRule = DomainRule(
        id = "1",
        domain = "facebook.com",
        enabled = true,
        matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
        scheduleMode = ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
        weeklySlots = slots,
    )
    val monday1900 = ZonedDateTime.of(2026, 6, 22, 19, 0, 0, 0, ZoneId.of("Europe/Warsaw"))
    val monday1800 = monday1900.minusHours(1)
    assertFalse(RuleEvaluator.shouldBlock(allowRule, monday1900))
    assertTrue(RuleEvaluator.shouldBlock(allowRule, monday1800))
}

private fun testLockManager() {
    val manager = LockManager()
    val pending = manager.startUnlockCountdown(elapsedRealtimeMs = 1_000L, bootCount = 5)
    val earlyStatus = manager.getUnlockStatus(pending, elapsedRealtimeMs = 1_500L, bootCount = 5)
    assertEquals("UNLOCK_PENDING", earlyStatus.state)
    assertFalse(earlyStatus.canConfirm)
    val readyStatus = manager.getUnlockStatus(pending, elapsedRealtimeMs = 301_000L, bootCount = 5)
    assertTrue(readyStatus.canConfirm)
    val unlocked = manager.confirmUnlock(pending, elapsedRealtimeMs = 301_000L, bootCount = 5)
    assertEquals(EditLockState.Unlocked, unlocked)
    val rebootedStatus = manager.getUnlockStatus(pending, elapsedRealtimeMs = 301_000L, bootCount = 6)
    assertEquals("LOCKED", rebootedStatus.state)
}

private fun testConfigRepositoryPersistence() {
    val file = java.io.File.createTempFile("focusgate-config", ".txt")
    file.delete()
    val repository = FileConfigRepository(file)
    val first = repository.write { config ->
        config.copy(
            lockState = EditLockState.Unlocked,
            rules =
                listOf(
                    DomainRule(
                        id = "1",
                        domain = "facebook.com",
                        enabled = true,
                        matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                        scheduleMode = ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
                        weeklySlots = BooleanArray(168),
                    ),
                ),
        )
    }
    assertEquals(1L, first.revision)
    val restored = repository.read()
    assertEquals(EditLockState.Unlocked, restored.lockState)
    assertEquals("facebook.com", restored.rules.single().domain)
}

private fun testDnsPolicyEvaluator() {
    val slots = BooleanArray(168)
    slots[RuleEvaluator.getSlotIndex(0, 18)] = true
    val configRepository =
        FileConfigRepository(java.io.File.createTempFile("focusgate-dns", ".txt").apply { delete() })
    val config =
        configRepository.write {
            it.copy(
                rules =
                    listOf(
                        DomainRule(
                            id = "rule-1",
                            domain = "facebook.com",
                            enabled = true,
                            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
                            weeklySlots = slots,
                        ),
                    ),
            )
        }
    val evaluator = DnsPolicyEvaluator()
    val blockedNow = ZonedDateTime.of(2026, 6, 22, 18, 15, 0, 0, ZoneId.of("Europe/Warsaw"))
    val allowedLater = blockedNow.plusHours(1)
    val blockedDecision = evaluator.evaluate("m.facebook.com", blockedNow, config)
    assertEquals(DnsAction.BLOCK, blockedDecision.action)
    assertEquals("rule-1", blockedDecision.matchedRuleId)
    val allowedDecision = evaluator.evaluate("facebook.com", allowedLater, config)
    assertEquals(DnsAction.ALLOW, allowedDecision.action)
}

private fun testDnsMessageCodec() {
    val queryPacket = buildDnsQueryPacket(transactionId = 0x1234, domain = "facebook.com")
    val parsed = DnsMessageCodec.parseQuery(queryPacket)
    assertEquals(0x1234, parsed.transactionId)
    assertEquals("facebook.com", parsed.question.name)
    val responsePacket = DnsMessageCodec.buildNxdomainResponse(queryPacket)
    assertEquals(queryPacket.size, responsePacket.size)
    assertEquals(0x1234, responsePacket.readUnsignedShort(0))
    assertEquals(0x8403, responsePacket.readUnsignedShort(2))

    val ednsQueryPacket = buildDnsQueryPacket(transactionId = 0x1235, domain = "facebook.com", includeEdns = true)
    val parsedEdns = DnsMessageCodec.parseQuery(ednsQueryPacket)
    assertEquals(0x1235, parsedEdns.transactionId)
    assertEquals("facebook.com", parsedEdns.question.name)
}

private fun testDnsTcpCodec() {
    val queryPacket = buildDnsQueryPacket(transactionId = 0x2222, domain = "facebook.com")
    val encoded = DnsTcpCodec.encodeQuery(queryPacket)
    assertEquals(queryPacket.size + 2, encoded.size)
    val decoded = DnsTcpCodec.decodeResponse(encoded)
    assertByteArrayEquals(queryPacket, decoded)
    val parsed = DnsTcpCodec.parseQuery(encoded)
    assertEquals(0x2222, parsed.transactionId)
    assertEquals("facebook.com", parsed.question.name)
}

private fun testDnsPacketProcessor() {
    val blockedSlots = BooleanArray(168)
    blockedSlots[RuleEvaluator.getSlotIndex(0, 18)] = true
    val configRepository =
        FileConfigRepository(java.io.File.createTempFile("focusgate-packet", ".txt").apply { delete() })
    val config =
        configRepository.write {
            it.copy(
                rules =
                    listOf(
                        DomainRule(
                            id = "rule-1",
                            domain = "facebook.com",
                            enabled = true,
                            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
                            weeklySlots = blockedSlots,
                        ),
                    ),
            )
        }
    val processor = DnsPacketProcessor()
    val now = ZonedDateTime.of(2026, 6, 22, 18, 15, 0, 0, ZoneId.of("Europe/Warsaw"))

    val blockedResult = processor.processQuery(buildDnsQueryPacket(0x1000, "m.facebook.com"), now, config)
    when (blockedResult) {
        is PacketProcessingResult.Respond -> {
            assertEquals("SCHEDULE_BLOCKED", blockedResult.reason)
            assertEquals(0x8403, blockedResult.packet.readUnsignedShort(2))
        }
        is PacketProcessingResult.Forward -> error("Expected blocked response")
    }

    val allowedResult = processor.processQuery(buildDnsQueryPacket(0x1001, "reddit.com"), now, config)
    when (allowedResult) {
        is PacketProcessingResult.Forward -> {
            assertEquals("reddit.com", allowedResult.questionName)
            assertEquals("NO_MATCHING_RULE", allowedResult.reason)
        }
        is PacketProcessingResult.Respond -> error("Expected forward result")
    }
}

private fun testIpv4UdpPacketCodec() {
    val dnsPayload = buildDnsQueryPacket(0x3000, "facebook.com")
    val requestPacket = buildIpv4UdpPacket("10.10.0.2", "1.1.1.1", 53000, 53, dnsPayload)
    val parsed = Ipv4UdpPacketCodec.parse(requestPacket)
    assertEquals(53, parsed.destinationPort)
    assertEquals(53000, parsed.sourcePort)
    assertByteArrayEquals(dnsPayload, parsed.payload)

    val responsePayload = DnsMessageCodec.buildNxdomainResponse(dnsPayload)
    val responsePacket = Ipv4UdpPacketCodec.buildResponse(parsed, responsePayload)
    val reparsed = Ipv4UdpPacketCodec.parse(responsePacket)
    assertEquals(53000, reparsed.destinationPort)
    assertEquals(53, reparsed.sourcePort)
    assertByteArrayEquals(responsePayload, reparsed.payload)
}

private fun testIpv6UdpPacketCodec() {
    val dnsPayload = buildDnsQueryPacket(0x3001, "facebook.com")
    val requestPacket =
        buildIpv6UdpPacket(
            "2001:db8:0:0:0:0:0:1",
            "2001:4860:4860:0:0:0:0:8888",
            53001,
            53,
            dnsPayload,
        )
    val parsed = Ipv6UdpPacketCodec.parse(requestPacket)
    assertEquals(53, parsed.destinationPort)
    assertEquals(53001, parsed.sourcePort)
    assertByteArrayEquals(dnsPayload, parsed.payload)

    val responsePayload = DnsMessageCodec.buildNxdomainResponse(dnsPayload)
    val responsePacket = Ipv6UdpPacketCodec.buildResponse(parsed, responsePayload)
    val reparsed = Ipv6UdpPacketCodec.parse(responsePacket)
    assertEquals(53001, reparsed.destinationPort)
    assertEquals(53, reparsed.sourcePort)
    assertByteArrayEquals(responsePayload, reparsed.payload)
}

private fun testTunPacketHandler() {
    val allowedResponse = buildDnsQueryPacket(0x4001, "reddit.com")
    val vpnService =
        FocusGateVpnService(
            upstreamDnsClient =
                UpstreamDnsClient(
                    socketProtector =
                        object : VpnSocketProtector {
                            override fun protect(host: String, port: Int): Boolean = true
                        },
                    transport =
                        object : UpstreamDnsTransport {
                            override fun send(request: UpstreamDnsRequest): UpstreamDnsResult =
                                UpstreamDnsResult(responsePacket = allowedResponse)
                        },
                ),
        )
    val slots = BooleanArray(168)
    slots[RuleEvaluator.getSlotIndex(0, 18)] = true
    val configRepository =
        FileConfigRepository(java.io.File.createTempFile("focusgate-tun", ".txt").apply { delete() })
    val config =
        configRepository.write {
            it.copy(
                rules =
                    listOf(
                        DomainRule(
                            id = "rule-1",
                            domain = "facebook.com",
                            enabled = true,
                            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
                            weeklySlots = slots,
                        ),
                    ),
            )
        }
    vpnService.start(config)
    val handler = TunPacketHandler(vpnService)
    val now = ZonedDateTime.of(2026, 6, 22, 18, 15, 0, 0, ZoneId.of("Europe/Warsaw"))

    val blockedResult = handler.handle(buildIpv4UdpPacket("10.10.0.2", "1.1.1.1", 53000, 53, buildDnsQueryPacket(0x4000, "m.facebook.com")), now, config)
    when (blockedResult) {
        is TunPacketResult.Respond -> {
            val reparsed = Ipv4UdpPacketCodec.parse(blockedResult.packet)
            assertEquals(53000, reparsed.destinationPort)
            assertEquals(53, reparsed.sourcePort)
        }
        is TunPacketResult.Drop -> error("Expected DNS response")
    }

    val nonDnsResult = handler.handle(buildIpv4UdpPacket("10.10.0.2", "1.1.1.1", 53000, 80, buildDnsQueryPacket(0x4002, "facebook.com")), now, config)
    assertEquals(TunPacketResult.Drop(TunDropReason.NON_DNS_PORT), nonDnsResult)

    val blockedIpv6Result =
        handler.handle(
            buildIpv6UdpPacket(
                "2001:db8:0:0:0:0:0:1",
                "2001:4860:4860:0:0:0:0:8888",
                53000,
                53,
                buildDnsQueryPacket(0x4003, "m.facebook.com"),
            ),
            now,
            config,
        )
    when (blockedIpv6Result) {
        is TunPacketResult.Respond -> {
            val reparsed = Ipv6UdpPacketCodec.parse(blockedIpv6Result.packet)
            assertEquals(53000, reparsed.destinationPort)
            assertEquals(53, reparsed.sourcePort)
        }
        is TunPacketResult.Drop -> error("Expected IPv6 DNS response")
    }

    val malformed = handler.handle(byteArrayOf(1, 2, 3), now, config)
    assertEquals(TunPacketResult.Drop(TunDropReason.MALFORMED_PACKET), malformed)
}

private fun testUpstreamDnsClientProtection() {
    val calls = mutableListOf<String>()
    val client =
        UpstreamDnsClient(
            socketProtector =
                object : VpnSocketProtector {
                    override fun protect(host: String, port: Int): Boolean {
                        calls += "$host:$port"
                        return true
                    }
                },
            transport =
                object : UpstreamDnsTransport {
                    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult {
                        assertEquals("1.1.1.1", request.host)
                        assertEquals(53, request.port)
                        return UpstreamDnsResult(responsePacket = byteArrayOf(1, 2, 3))
                    }
                },
        )

    val forwarded =
        client.forward(
            packet = byteArrayOf(9, 9),
            vpnConfig =
                dev.pawelsowa.focusgate.config.VpnConfig(
                    upstreamDnsIp = "1.1.1.1",
                    upstreamDnsPort = 53,
                    filteredApplications = listOf("com.brave.browser"),
                ),
        )

    assertEquals(listOf("1.1.1.1:53"), calls)
    assertByteArrayEquals(byteArrayOf(1, 2, 3), forwarded)
}

private fun testUpstreamDnsClientRetries() {
    var attempts = 0
    val client =
        UpstreamDnsClient(
            socketProtector =
                object : VpnSocketProtector {
                    override fun protect(host: String, port: Int): Boolean = true
                },
            transport =
                object : UpstreamDnsTransport {
                    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult {
                        attempts += 1
                        if (attempts == 1) {
                            throw IllegalStateException("timeout")
                        }
                        return UpstreamDnsResult(responsePacket = byteArrayOf(7, 7))
                    }
                },
            maxAttempts = 2,
        )

    val forwarded =
        client.forward(
            packet = byteArrayOf(1, 1),
            vpnConfig =
                dev.pawelsowa.focusgate.config.VpnConfig(
                    upstreamDnsIp = "1.1.1.1",
                    upstreamDnsPort = 53,
                    filteredApplications = listOf("com.brave.browser"),
                ),
        )
    assertEquals(2, attempts)
    assertByteArrayEquals(byteArrayOf(7, 7), forwarded)

    val failingClient =
        UpstreamDnsClient(
            socketProtector =
                object : VpnSocketProtector {
                    override fun protect(host: String, port: Int): Boolean = true
                },
            transport =
                object : UpstreamDnsTransport {
                    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult {
                        throw IllegalStateException("timeout")
                    }
                },
            maxAttempts = 2,
        )

    try {
        failingClient.forward(
            packet = byteArrayOf(1, 1),
            vpnConfig =
                dev.pawelsowa.focusgate.config.VpnConfig(
                    upstreamDnsIp = "1.1.1.1",
                    upstreamDnsPort = 53,
                    filteredApplications = listOf("com.brave.browser"),
                ),
        )
        error("Expected upstream failure")
    } catch (error: IllegalStateException) {
        assertEquals("UPSTREAM_DNS_UNAVAILABLE", error.message)
    }

    val truncatedUdpResponse = buildDnsResponsePacket(transactionId = 0x3333, truncated = true)
    val tcpPayload = buildDnsResponsePacket(transactionId = 0x3333, truncated = false)
    val fallbackClient =
        UpstreamDnsClient(
            socketProtector =
                object : VpnSocketProtector {
                    override fun protect(host: String, port: Int): Boolean = true
                },
            transport =
                object : UpstreamDnsTransport {
                    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult =
                        UpstreamDnsResult(responsePacket = truncatedUdpResponse)
                },
            tcpTransport =
                object : UpstreamDnsTransport {
                    override fun send(request: UpstreamDnsRequest): UpstreamDnsResult =
                        UpstreamDnsResult(responsePacket = DnsTcpCodec.encodeQuery(tcpPayload))
                },
            maxAttempts = 1,
        )

    val fallbackResponse =
        fallbackClient.forward(
            packet = buildDnsQueryPacket(0x3333, "facebook.com"),
            vpnConfig =
                dev.pawelsowa.focusgate.config.VpnConfig(
                    upstreamDnsIp = "1.1.1.1",
                    upstreamDnsPort = 53,
                    filteredApplications = listOf("com.brave.browser"),
                ),
        )
    assertByteArrayEquals(tcpPayload, fallbackResponse)
}

private fun testVpnServiceDnsHandling() {
    val allowedResponse = buildDnsQueryPacket(0x2001, "reddit.com")
    val vpnService =
        FocusGateVpnService(
            upstreamDnsClient =
                UpstreamDnsClient(
                    socketProtector =
                        object : VpnSocketProtector {
                            override fun protect(host: String, port: Int): Boolean = true
                        },
                    transport =
                        object : UpstreamDnsTransport {
                            override fun send(request: UpstreamDnsRequest): UpstreamDnsResult =
                                UpstreamDnsResult(responsePacket = allowedResponse)
                        },
                ),
        )
    val slots = BooleanArray(168)
    slots[RuleEvaluator.getSlotIndex(0, 18)] = true
    val configRepository =
        FileConfigRepository(java.io.File.createTempFile("focusgate-vpn", ".txt").apply { delete() })
    val config =
        configRepository.write {
            it.copy(
                rules =
                    listOf(
                        DomainRule(
                            id = "rule-1",
                            domain = "facebook.com",
                            enabled = true,
                            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
                            weeklySlots = slots,
                        ),
                    ),
            )
        }
    vpnService.start(config)
    assertEquals("com.brave.browser", vpnService.activeSession()?.filteredApplications?.single())

    val now = ZonedDateTime.of(2026, 6, 22, 18, 15, 0, 0, ZoneId.of("Europe/Warsaw"))
    val blocked = vpnService.handleDnsPacket(buildDnsQueryPacket(0x2000, "m.facebook.com"), now, config)
    assertEquals("BLOCK", blocked.action)
    assertEquals(0x8403, blocked.responsePacket.readUnsignedShort(2))

    val allowed = vpnService.handleDnsPacket(buildDnsQueryPacket(0x2001, "reddit.com"), now, config)
    assertEquals("ALLOW", allowed.action)
    assertEquals("reddit.com", allowed.domain)
    assertByteArrayEquals(allowedResponse, allowed.responsePacket)
}

private fun testVpnReporting() {
    val vpnService = FocusGateVpnService()
    val slots = BooleanArray(168)
    slots[RuleEvaluator.getSlotIndex(0, 18)] = true
    val configRepository =
        FileConfigRepository(java.io.File.createTempFile("focusgate-report", ".txt").apply { delete() })
    val config =
        configRepository.write {
            it.copy(
                rules =
                    listOf(
                        DomainRule(
                            id = "rule-1",
                            domain = "facebook.com",
                            enabled = true,
                            matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
                            scheduleMode = ScheduleMode.BLOCK_DURING_SELECTED_HOURS,
                            weeklySlots = slots,
                        ),
                    ),
            )
        }
    val now = ZonedDateTime.of(2026, 6, 22, 18, 15, 0, 0, ZoneId.of("Europe/Warsaw"))

    val inactiveNotification = vpnService.buildForegroundNotification(config, now)
    assertEquals("FocusGate is inactive", inactiveNotification.title)
    assertEquals("1 domains are currently blocked", inactiveNotification.message)

    val inactiveDiagnostics = vpnService.buildDiagnostics(config, now)
    assertEquals(false, inactiveDiagnostics.vpnActive)
    assertEquals("Inactive", inactiveDiagnostics.dnsInterception)
    assertEquals("Pending", inactiveDiagnostics.braveFilteringTest)

    vpnService.start(config)
    val activeNotification = vpnService.buildForegroundNotification(config, now)
    assertEquals("FocusGate is active", activeNotification.title)
    assertEquals(listOf("Open", "View status"), activeNotification.actions)

    val activeDiagnostics = vpnService.buildDiagnostics(config, now)
    assertEquals(true, activeDiagnostics.vpnActive)
    assertEquals("Working", activeDiagnostics.dnsInterception)
    assertEquals("Passed", activeDiagnostics.braveFilteringTest)
    assertEquals(1, activeDiagnostics.blockedDomainsNow)

    vpnService.onNetworkChanged(false)
    val offlineDiagnostics = vpnService.buildDiagnostics(config, now)
    assertEquals("Network unavailable", offlineDiagnostics.dnsInterception)
    assertEquals(false, offlineDiagnostics.networkAvailable)

    vpnService.onNetworkChanged(true)
    val restartNotification = vpnService.buildForegroundNotification(config, now)
    assertEquals("FocusGate restart required", restartNotification.title)
    val restartDiagnostics = vpnService.buildDiagnostics(config, now)
    assertEquals(true, restartDiagnostics.restartRequired)
    assertEquals("NETWORK_CHANGED", restartDiagnostics.restartReason)
}

private fun testVpnRuntimeController() {
    val controller = dev.pawelsowa.focusgate.vpn.VpnRuntimeController()
    assertEquals(false, controller.currentState().restartRequired)
    controller.markServiceRecreated()
    assertEquals(true, controller.currentState().restartRequired)
    assertEquals(dev.pawelsowa.focusgate.vpn.RestartReason.SERVICE_RECREATED, controller.currentState().restartReason)
    controller.acknowledgeRestart()
    assertEquals(false, controller.currentState().restartRequired)
    controller.onNetworkChanged(false)
    assertEquals(false, controller.currentState().networkAvailable)
    controller.onNetworkChanged(true)
    assertEquals(true, controller.currentState().restartRequired)
    assertEquals(dev.pawelsowa.focusgate.vpn.RestartReason.NETWORK_CHANGED, controller.currentState().restartReason)
}

private fun testNativeBridgeLockAndPersistence() {
    val file = java.io.File.createTempFile("focusgate-bridge", ".txt")
    file.delete()
    val repository = FileConfigRepository(file)
    val deviceContext = MutableDeviceContext(elapsedRealtimeMsValue = 1_000L, bootCountValue = 7)
    val bridge =
        FocusGateBridge(
            repository = repository,
            vpnService = FocusGateVpnService(),
            deviceContext = deviceContext,
        )

    runSuspend { bridge.startUnlockCountdown() }
    deviceContext.setElapsedRealtimeMs(301_000L)
    runSuspend { bridge.confirmUnlock() }
    runSuspend {
        bridge.addRule(
            DomainRuleDto(
                id = "1",
                domain = " Facebook.com ",
                enabled = true,
                matchMode = "DOMAIN_AND_SUBDOMAINS",
                scheduleMode = "ALLOW_ONLY_DURING_SELECTED_HOURS",
                weeklySlots = List(168) { false },
            ),
        )
    }

    val afterAdd = runSuspend { bridge.getConfig() }
    assertEquals("LOCKED", afterAdd.lockState)
    assertEquals("facebook.com", afterAdd.rules.single().domain)
    assertFails("EDITING_LOCKED") {
        runSuspend {
            bridge.deleteRule("1")
        }
    }
    runSuspend { bridge.startVpn() }
    assertEquals("RUNNING", runSuspend { bridge.getVpnStatus() })

    runSuspend { bridge.startUnlockCountdown() }
    deviceContext.setElapsedRealtimeMs(601_000L)
    runSuspend { bridge.confirmUnlock() }
    runSuspend {
        bridge.updateVpnConfig(
            VpnConfigDto(
                filteredApplications = listOf("com.brave.browser"),
                upstreamDns = UpstreamDnsDto(ip = "8.8.8.8", port = 53),
            ),
        )
    }
    val afterVpnConfig = runSuspend { bridge.getConfig() }
    assertEquals(listOf("com.brave.browser"), afterVpnConfig.filteredApplications)
    assertEquals("8.8.8.8", afterVpnConfig.upstreamDns.ip)
    assertEquals(53, afterVpnConfig.upstreamDns.port)

    val exportedConfig = runSuspend { bridge.exportConfig() }
    assertEquals("8.8.8.8", exportedConfig.upstreamDns.ip)

    runSuspend { bridge.startUnlockCountdown() }
    deviceContext.setElapsedRealtimeMs(901_000L)
    runSuspend { bridge.confirmUnlock() }
    runSuspend {
        bridge.importConfig(
            AppConfigDto(
                rules =
                    listOf(
                        DomainRuleDto(
                            id = "2",
                            domain = "reddit.com",
                            enabled = true,
                            matchMode = "DOMAIN_AND_SUBDOMAINS",
                            scheduleMode = "ALLOW_ONLY_DURING_SELECTED_HOURS",
                            weeklySlots = List(168) { false },
                        ),
                    ),
                lockState = "UNLOCKED",
                vpnStatus = "STOPPED",
                filteredApplications = listOf("com.brave.browser"),
                upstreamDns = UpstreamDnsDto(ip = "9.9.9.9", port = 53),
            ),
        )
    }
    val importedConfig = runSuspend { bridge.getConfig() }
    assertEquals("reddit.com", importedConfig.rules.single().domain)
    assertEquals("9.9.9.9", importedConfig.upstreamDns.ip)

    runSuspend { bridge.startUnlockCountdown() }
    deviceContext.setElapsedRealtimeMs(1_201_000L)
    runSuspend { bridge.confirmUnlock() }
    runSuspend { bridge.resetConfig() }
    val resetConfig = runSuspend { bridge.getConfig() }
    assertEquals(0, resetConfig.rules.size)
    assertEquals("1.1.1.1", resetConfig.upstreamDns.ip)
}

private fun assertEquals(expected: Any?, actual: Any?) {
    if (expected != actual) {
        error("Expected <$expected>, got <$actual>")
    }
}

private fun assertTrue(value: Boolean) {
    if (!value) {
        error("Expected true")
    }
}

private fun assertFalse(value: Boolean) {
    if (value) {
        error("Expected false")
    }
}

private fun assertFails(expectedMessage: String, block: () -> Unit) {
    try {
        block()
        error("Expected failure")
    } catch (error: IllegalArgumentException) {
        assertEquals(expectedMessage, error.message)
    }
}

private fun assertByteArrayEquals(expected: ByteArray, actual: ByteArray) {
    if (!expected.contentEquals(actual)) {
        error("Expected <${expected.joinToString()}>, got <${actual.joinToString()}>")
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var value: T? = null
    var failure: Throwable? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
            }
        },
    )
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}

private fun buildDnsQueryPacket(
    transactionId: Int,
    domain: String,
    includeEdns: Boolean = false,
): ByteArray {
    val labels = domain.split('.')
    val questionBytes = mutableListOf<Byte>()

    labels.forEach { label ->
        questionBytes += label.length.toByte()
        questionBytes += label.encodeToByteArray().toList()
    }
    questionBytes += 0
    questionBytes += 0
    questionBytes += 1
    questionBytes += 0
    questionBytes += 1

    val header = ByteArray(12)
    header.writeUnsignedShort(0, transactionId)
    header.writeUnsignedShort(2, 0x0100)
    header.writeUnsignedShort(4, 1)
    header.writeUnsignedShort(6, 0)
    header.writeUnsignedShort(8, 0)
    header.writeUnsignedShort(10, if (includeEdns) 1 else 0)

    val additional =
        if (!includeEdns) {
            byteArrayOf()
        } else {
            byteArrayOf(
                0,
                0,
                41,
                16.toByte(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )
        }

    return header + questionBytes.toByteArray() + additional
}

private fun buildIpv4UdpPacket(
    sourceIp: String,
    destinationIp: String,
    sourcePort: Int,
    destinationPort: Int,
    payload: ByteArray,
): ByteArray {
    val udpLength = 8 + payload.size
    val totalLength = 20 + udpLength
    val packet = ByteArray(totalLength)
    packet[0] = 0x45
    packet[1] = 0
    packet.writeUnsignedShort(2, totalLength)
    packet.writeUnsignedShort(4, 0)
    packet.writeUnsignedShort(6, 0)
    packet[8] = 64
    packet[9] = 17
    sourceIp.split('.').map(String::toInt).forEachIndexed { index, octet ->
        packet[12 + index] = octet.toByte()
    }
    destinationIp.split('.').map(String::toInt).forEachIndexed { index, octet ->
        packet[16 + index] = octet.toByte()
    }
    packet.writeUnsignedShort(20, sourcePort)
    packet.writeUnsignedShort(22, destinationPort)
    packet.writeUnsignedShort(24, udpLength)
    packet.writeUnsignedShort(26, 0)
    payload.copyInto(packet, destinationOffset = 28)
    return packet
}

private fun buildIpv6UdpPacket(
    sourceIp: String,
    destinationIp: String,
    sourcePort: Int,
    destinationPort: Int,
    payload: ByteArray,
): ByteArray {
    val udpLength = 8 + payload.size
    val packet = ByteArray(40 + udpLength)
    packet[0] = 0x60
    packet[1] = 0
    packet[2] = 0
    packet[3] = 0
    packet.writeUnsignedShort(4, udpLength)
    packet[6] = 17
    packet[7] = 64
    encodeIpv6Address(sourceIp).copyInto(packet, destinationOffset = 8)
    encodeIpv6Address(destinationIp).copyInto(packet, destinationOffset = 24)
    packet.writeUnsignedShort(40, sourcePort)
    packet.writeUnsignedShort(42, destinationPort)
    packet.writeUnsignedShort(44, udpLength)
    packet.writeUnsignedShort(46, 0)
    payload.copyInto(packet, destinationOffset = 48)
    return packet
}

private fun encodeIpv6Address(ip: String): ByteArray {
    val segments = ip.split("::")
    val left = if (segments[0].isEmpty()) emptyList() else segments[0].split(':')
    val right = if (segments.size == 1 || segments[1].isEmpty()) emptyList() else segments[1].split(':')
    val missingCount = 8 - left.size - right.size
    val full =
        buildList {
            addAll(left)
            repeat(missingCount) { add("0") }
            addAll(right)
        }
    return ByteArray(16).also { bytes ->
        full.forEachIndexed { index, segment ->
            val value = segment.toInt(16)
            bytes[index * 2] = ((value ushr 8) and 0xFF).toByte()
            bytes[index * 2 + 1] = (value and 0xFF).toByte()
        }
    }
}

private fun buildDnsResponsePacket(
    transactionId: Int,
    truncated: Boolean,
): ByteArray {
    val packet = ByteArray(12)
    packet.writeUnsignedShort(0, transactionId)
    packet.writeUnsignedShort(2, 0x8000 or if (truncated) 0x0200 else 0)
    packet.writeUnsignedShort(4, 0)
    packet.writeUnsignedShort(6, 0)
    packet.writeUnsignedShort(8, 0)
    packet.writeUnsignedShort(10, 0)
    return packet
}

private fun ByteArray.readUnsignedShort(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.writeUnsignedShort(
    offset: Int,
    value: Int,
) {
    this[offset] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 1] = (value and 0xFF).toByte()
}
