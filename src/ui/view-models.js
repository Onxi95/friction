import { shouldBlock, summarizeSchedule } from '../domain/schedule.js';

export function buildDashboardViewModel(config, now) {
  const blockedCount = config.rules.filter((rule) => shouldBlock(rule, now)).length;

  return {
    vpnStatus: config.vpnStatus,
    lockState: config.lockState.type,
    activeRules: config.rules.filter((rule) => rule.enabled).length,
    blockedCount,
    warning: config.vpnStatus === 'RUNNING' ? null : 'Filtering inactive',
  };
}

export function buildDomainListViewModel(config, now) {
  return config.rules.map((rule) => ({
    id: rule.id,
    domain: rule.domain,
    enabled: rule.enabled,
    status: !rule.enabled
      ? 'Rule disabled'
      : config.vpnStatus !== 'RUNNING'
        ? 'VPN inactive'
        : shouldBlock(rule, now)
          ? 'Blocked now'
          : 'Allowed now',
    summary: summarizeSchedule(rule.domain, rule.weeklySlots, rule.scheduleMode),
  }));
}

export function buildLockViewModel(lockStatus) {
  if (lockStatus.state === 'UNLOCKED') {
    return {
      title: 'Editing unlocked',
      action: 'Lock editing',
    };
  }

  if (lockStatus.state === 'LOCKED') {
    return {
      title: 'Editing locked',
      action: 'Start unlocking',
    };
  }

  return {
    title: lockStatus.canConfirm ? 'Ready to unlock' : 'Unlock pending',
    action: lockStatus.canConfirm ? 'Unlock editing' : 'Waiting',
    remainingMs: lockStatus.remainingMs,
  };
}

export function buildDiagnosticsViewModel(config, now) {
  const blockedCount = config.rules.filter((rule) => shouldBlock(rule, now)).length;
  const vpnActive = config.vpnStatus === 'RUNNING';
  const hasRules = config.rules.length > 0;

  return {
    vpnActive: vpnActive ? 'Yes' : 'No',
    dnsInterception: vpnActive ? 'Working' : 'Inactive',
    braveFilteringTest: vpnActive && hasRules ? 'Passed' : hasRules ? 'Pending' : 'Not configured',
    currentlyBlocked: blockedCount,
  };
}

export function buildOnboardingViewModel(config) {
  const braveIncluded = config.filteredApplications?.includes('com.brave.browser') ?? true;

  return {
    secureDnsWarning:
      'Disable Secure DNS in Brave or configure it to use the current provider. Otherwise FocusGate may not see DNS requests and domain filtering may not work.',
    alwaysOnVpnSteps: [
      'Settings',
      'Network and Internet',
      'VPN',
      'FocusGate',
      'Always-on VPN',
    ],
    braveStatus: braveIncluded ? 'Brave configured for filtering' : 'Brave missing from filtered applications',
    upstreamDnsSummary: `${config.upstreamDns?.ip ?? '1.1.1.1'}:${config.upstreamDns?.port ?? 53}`,
  };
}
