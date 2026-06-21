function renderRows(rows) {
  return rows.join('\n');
}

export function renderDashboardScreen(state) {
  const lines = [
    'FocusGate',
    '',
    'VPN',
    state.dashboard.vpnStatus,
    '',
    'Editing',
    state.dashboard.lockState,
    '',
    'Currently blocked',
    `${state.dashboard.blockedCount} domains`,
  ];

  if (state.dashboard.warning) {
    lines.push('', state.dashboard.warning);
  }

  lines.push('', '[View domains]', '[VPN settings]');
  return renderRows(lines);
}

export function renderDomainListScreen(state) {
  const lines = ['Domains', ''];

  state.domains.forEach((domain) => {
    lines.push(domain.domain);
    lines.push(`${domain.status} · ${domain.summary}`);
    lines.push('');
  });

  lines.push('[Add domain]');
  return renderRows(lines);
}

export function renderEditorScreen(state) {
  if (!state.editor) {
    return 'Editor closed';
  }

  const lines = [
    'Domain',
    `[ ${state.editor.domain} ]`,
    '',
    'Match mode',
    state.editor.matchMode,
    '',
    'Schedule mode',
    state.editor.scheduleMode,
    '',
    'Summary',
    state.editor.summary,
  ];

  return renderRows(lines);
}

export function renderLockScreen(state) {
  const lines = [
    'Editing',
    state.lock.title,
    '',
    'Action',
    state.lock.action,
  ];

  if (typeof state.lock.remainingMs === 'number') {
    lines.push('', 'Remaining', `${state.lock.remainingMs} ms`);
  }

  return renderRows(lines);
}

export function renderDiagnosticsScreen(state) {
  return renderRows([
    'Diagnostics',
    '',
    `VPN active: ${state.diagnostics.vpnActive}`,
    `DNS interception: ${state.diagnostics.dnsInterception}`,
    `Brave filtering test: ${state.diagnostics.braveFilteringTest}`,
    `Currently blocked: ${state.diagnostics.currentlyBlocked}`,
  ]);
}

export function renderOnboardingScreen(state) {
  return renderRows([
    'Onboarding',
    '',
    state.onboarding.secureDnsWarning,
    '',
    state.onboarding.braveStatus,
    `Upstream DNS: ${state.onboarding.upstreamDnsSummary}`,
    '',
    ...state.onboarding.alwaysOnVpnSteps,
  ]);
}

export function renderAppScreens(state) {
  return {
    dashboard: renderDashboardScreen(state),
    domains: renderDomainListScreen(state),
    editor: renderEditorScreen(state),
    lock: renderLockScreen(state),
    diagnostics: renderDiagnosticsScreen(state),
    onboarding: renderOnboardingScreen(state),
  };
}
