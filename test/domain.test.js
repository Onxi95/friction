import test from 'node:test';
import assert from 'node:assert/strict';
import { createDomainRule, MatchMode, ScheduleMode, createWeeklySlots } from '../src/domain/model.js';
import { matchesDomainAndSubdomains, matchesExact, matchesRule } from '../src/domain/matcher.js';
import { assertUniqueDomain, normalizeDomain } from '../src/domain/normalizer.js';
import {
  applyDragSelection,
  applyPreset,
  copyDay,
  getSlotIndex,
  pasteDay,
  shouldBlock,
  summarizeSchedule,
  toggleDay,
  toggleHour,
  toggleSlot,
} from '../src/domain/schedule.js';
import { confirmUnlock, createLockedState, createUnlockedState, getUnlockStatus, relockAfterWrite, requireEditingUnlocked, startUnlockCountdown, UNLOCK_DELAY_MS } from '../src/lock/lock.js';
import { ConfigStore } from '../src/state/config-store.js';
import { FocusGateController } from '../src/app/focusgate-controller.js';
import { renderAppScreens } from '../src/app/screen-renderer.js';
import { LocalFocusGateNativeModule } from '../src/native/local-native-module.js';
import { resolveNativeModule, RuntimeFocusGateNativeModule } from '../src/native/runtime-native-module.js';
import {
  buildDashboardViewModel,
  buildDiagnosticsViewModel,
  buildDomainListViewModel,
  buildLockViewModel,
  buildOnboardingViewModel,
} from '../src/ui/view-models.js';

test('normalizeDomain normalizes simple hostnames', () => {
  assert.equal(normalizeDomain(' Facebook.com. '), 'facebook.com');
});

test('normalizeDomain rejects URLs, paths, and ports', () => {
  assert.throws(() => normalizeDomain('https://facebook.com'), /INVALID_DOMAIN/);
  assert.throws(() => normalizeDomain('facebook.com/feed'), /INVALID_DOMAIN/);
  assert.throws(() => normalizeDomain('facebook.com:443'), /INVALID_DOMAIN/);
});

test('assertUniqueDomain rejects duplicates', () => {
  assert.throws(() => assertUniqueDomain('facebook.com', ['facebook.com']), /DUPLICATE_DOMAIN/);
});

test('domain matching honors exact and subdomain modes', () => {
  assert.equal(matchesExact('facebook.com', 'facebook.com'), true);
  assert.equal(matchesExact('www.facebook.com', 'facebook.com'), false);
  assert.equal(matchesDomainAndSubdomains('m.facebook.com', 'facebook.com'), true);
  assert.equal(matchesDomainAndSubdomains('notfacebook.com', 'facebook.com'), false);
  const rule = createDomainRule({
    id: '1',
    domain: 'facebook.com',
    enabled: true,
    matchMode: MatchMode.DOMAIN_AND_SUBDOMAINS,
    scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
  });
  assert.equal(matchesRule('m.facebook.com', rule), true);
});

test('getSlotIndex and shouldBlock evaluate weekly schedule', () => {
  const slots = createWeeklySlots(false);
  slots[getSlotIndex(0, 19)] = true;
  const allowRule = createDomainRule({
    id: '1',
    domain: 'facebook.com',
    enabled: true,
    scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
    weeklySlots: slots,
  });
  const monday1900 = new Date('2026-06-22T19:00:00+02:00');
  const monday1800 = new Date('2026-06-22T18:00:00+02:00');
  assert.equal(shouldBlock(allowRule, monday1900), false);
  assert.equal(shouldBlock(allowRule, monday1800), true);
});

test('schedule presets and toggles work', () => {
  const empty = createWeeklySlots(false);
  const toggled = toggleSlot(empty, 0, 9);
  assert.equal(toggled[getSlotIndex(0, 9)], true);
  const weekdays = applyPreset(empty, 'WEEKDAYS');
  assert.equal(weekdays[getSlotIndex(0, 12)], true);
  assert.equal(weekdays[getSlotIndex(5, 12)], false);
  const allMonday = toggleDay(empty, 0);
  assert.equal(allMonday[getSlotIndex(0, 0)], true);
  assert.equal(allMonday[getSlotIndex(0, 23)], true);
  const allAtNine = toggleHour(empty, 9);
  assert.equal(allAtNine[getSlotIndex(6, 9)], true);
  const dragged = applyDragSelection(empty, 0, 8, 0, 10, true);
  assert.equal(dragged[getSlotIndex(0, 8)], true);
  assert.equal(dragged[getSlotIndex(0, 10)], true);
  const copiedMonday = copyDay(allMonday, 0);
  const pastedTuesday = pasteDay(empty, 1, copiedMonday);
  assert.equal(pastedTuesday[getSlotIndex(1, 0)], true);
  const mondayToWeekdays = applyPreset(toggleSlot(empty, 0, 7), 'COPY_MONDAY_TO_WEEKDAYS');
  assert.equal(mondayToWeekdays[getSlotIndex(4, 7)], true);
  assert.equal(mondayToWeekdays[getSlotIndex(5, 7)], false);
});

test('schedule summary renders readable ranges', () => {
  const slots = createWeeklySlots(false);
  slots[getSlotIndex(0, 19)] = true;
  slots[getSlotIndex(0, 20)] = true;
  const summary = summarizeSchedule('facebook.com', slots, ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS);
  assert.match(summary, /facebook\.com is available/);
  assert.match(summary, /Monday 19:00-21:00/);
});

test('lock flow enforces countdown and relocking', () => {
  const pending = startUnlockCountdown(1_000, 3);
  const early = getUnlockStatus(pending, 10_000, 3);
  assert.deepEqual(early, {
    state: 'UNLOCK_PENDING',
    remainingMs: UNLOCK_DELAY_MS - 9_000,
    canConfirm: false,
  });
  const ready = getUnlockStatus(pending, 301_000, 3);
  assert.equal(ready.canConfirm, true);
  assert.deepEqual(confirmUnlock(pending, 301_000, 3), createUnlockedState());
  assert.deepEqual(getUnlockStatus(pending, 301_000, 4), { state: 'LOCKED' });
  assert.deepEqual(relockAfterWrite(), createLockedState());
  assert.throws(() => requireEditingUnlocked(createLockedState()), /EDITING_LOCKED/);
});

test('config store enforces native-style validation and relock after writes', () => {
  const store = new ConfigStore({ lockState: createUnlockedState() });
  store.addRule({
    id: '1',
    domain: 'facebook.com',
    enabled: true,
    scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
    matchMode: MatchMode.DOMAIN_AND_SUBDOMAINS,
    weeklySlots: createWeeklySlots(false),
  });
  assert.equal(store.getConfig().rules.length, 1);
  assert.deepEqual(store.getConfig().lockState, createLockedState());
  assert.throws(
    () =>
      store.addRule({
        id: '2',
        domain: 'reddit.com',
        enabled: true,
        scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
        matchMode: MatchMode.DOMAIN_AND_SUBDOMAINS,
        weeklySlots: createWeeklySlots(false),
      }),
    /EDITING_LOCKED/,
  );
  const unlockedStore = new ConfigStore({ lockState: createUnlockedState() });
  unlockedStore.updateVpnConfig({
    filteredApplications: ['com.brave.browser'],
    upstreamDns: { ip: '8.8.8.8', port: 53 },
  });
  assert.deepEqual(unlockedStore.getConfig().upstreamDns, { ip: '8.8.8.8', port: 53 });
  assert.deepEqual(unlockedStore.getConfig().lockState, createLockedState());
  const exportStore = new ConfigStore({ lockState: createUnlockedState() });
  exportStore.importConfig({
    rules: [],
    filteredApplications: ['com.brave.browser'],
    upstreamDns: { ip: '9.9.9.9', port: 53 },
  });
  assert.deepEqual(exportStore.exportConfig().upstreamDns, { ip: '9.9.9.9', port: 53 });
  exportStore.lockState = createUnlockedState();
  exportStore.resetConfig();
  assert.deepEqual(exportStore.exportConfig().upstreamDns, { ip: '1.1.1.1', port: 53 });
});

test('view models expose dashboard, domain list, and lock screen state', () => {
  const slots = createWeeklySlots(false);
  slots[getSlotIndex(0, 19)] = true;
  const config = {
    lockState: createLockedState(),
    vpnStatus: 'RUNNING',
    rules: [
      createDomainRule({
        id: '1',
        domain: 'facebook.com',
        enabled: true,
        scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
        weeklySlots: slots,
      }),
    ],
  };
  const now = new Date('2026-06-22T18:00:00+02:00');
  const dashboard = buildDashboardViewModel(config, now);
  assert.equal(dashboard.blockedCount, 1);
  const list = buildDomainListViewModel(config, now);
  assert.equal(list[0].status, 'Blocked now');
  const lock = buildLockViewModel({ state: 'LOCKED' });
  assert.equal(lock.action, 'Start unlocking');
  const diagnostics = buildDiagnosticsViewModel(config, now);
  assert.equal(diagnostics.vpnActive, 'Yes');
  assert.equal(diagnostics.braveFilteringTest, 'Passed');
  const onboarding = buildOnboardingViewModel({ filteredApplications: ['com.brave.browser'] });
  assert.match(onboarding.secureDnsWarning, /Secure DNS/);
  assert.equal(onboarding.upstreamDnsSummary, '1.1.1.1:53');
});

test('controller drives refresh, unlock, edit, save, and vpn flows', async () => {
  class FakeNativeModule {
    constructor() {
      this.config = {
        rules: [],
        lockState: 'LOCKED',
        vpnStatus: 'STOPPED',
        filteredApplications: ['com.brave.browser'],
        upstreamDns: { ip: '1.1.1.1', port: 53 },
      };
      this.unlockStatus = { state: 'LOCKED' };
      this.elapsed = 0;
    }

    async getConfig() {
      return {
        rules: this.config.rules.map((rule) => ({ ...rule, weeklySlots: [...rule.weeklySlots] })),
        lockState: this.config.lockState,
        vpnStatus: this.config.vpnStatus,
        filteredApplications: [...this.config.filteredApplications],
        upstreamDns: { ...this.config.upstreamDns },
      };
    }

    async addRule(rule) {
      if (this.config.lockState !== 'UNLOCKED') {
        throw new Error('EDITING_LOCKED');
      }

      this.config.rules = [...this.config.rules, { ...rule, weeklySlots: [...rule.weeklySlots] }];
      this.config.lockState = 'LOCKED';
      this.unlockStatus = { state: 'LOCKED' };
    }

    async updateRule(rule) {
      if (this.config.lockState !== 'UNLOCKED') {
        throw new Error('EDITING_LOCKED');
      }

      this.config.rules = this.config.rules.map((entry) => (entry.id === rule.id ? rule : entry));
      this.config.lockState = 'LOCKED';
      this.unlockStatus = { state: 'LOCKED' };
    }

    async deleteRule(ruleId) {
      if (this.config.lockState !== 'UNLOCKED') {
        throw new Error('EDITING_LOCKED');
      }

      this.config.rules = this.config.rules.filter((entry) => entry.id !== ruleId);
      this.config.lockState = 'LOCKED';
      this.unlockStatus = { state: 'LOCKED' };
    }

    async exportConfig() {
      return this.getConfig();
    }

    async importConfig(config) {
      if (this.config.lockState !== 'UNLOCKED') {
        throw new Error('EDITING_LOCKED');
      }

      this.config = {
        ...this.config,
        rules: config.rules.map((rule) => ({ ...rule, weeklySlots: [...rule.weeklySlots] })),
        filteredApplications: [...config.filteredApplications],
        upstreamDns: { ...config.upstreamDns },
        lockState: 'LOCKED',
      };
      this.unlockStatus = { state: 'LOCKED' };
    }

    async resetConfig() {
      if (this.config.lockState !== 'UNLOCKED') {
        throw new Error('EDITING_LOCKED');
      }

      this.config = {
        ...this.config,
        rules: [],
        filteredApplications: ['com.brave.browser'],
        upstreamDns: { ip: '1.1.1.1', port: 53 },
        lockState: 'LOCKED',
      };
      this.unlockStatus = { state: 'LOCKED' };
    }

    async startVpn() {
      this.config.vpnStatus = 'RUNNING';
    }

    async stopVpn() {
      this.config.vpnStatus = 'STOPPED';
    }

    async updateVpnConfig(config) {
      if (this.config.lockState !== 'UNLOCKED') {
        throw new Error('EDITING_LOCKED');
      }

      if (config.filteredApplications) {
        this.config.filteredApplications = [...config.filteredApplications];
      }

      if (config.upstreamDns) {
        this.config.upstreamDns = { ...config.upstreamDns };
      }

      this.config.lockState = 'LOCKED';
      this.unlockStatus = { state: 'LOCKED' };
    }

    async getVpnStatus() {
      return this.config.vpnStatus;
    }

    async getDiagnostics() {
      return {
        vpnActive: this.config.vpnStatus === 'RUNNING' ? 'Yes' : 'No',
        dnsInterception: this.config.vpnStatus === 'RUNNING' ? 'Working' : 'Inactive',
        braveFilteringTest: this.config.rules.length ? 'Passed' : 'Not configured',
        currentlyBlocked: 0,
        restartRequired: false,
        restartReason: null,
        networkAvailable: true,
      };
    }

    async enableEditLock() {
      this.config.lockState = 'LOCKED';
      this.unlockStatus = { state: 'LOCKED' };
    }

    async startUnlockCountdown() {
      this.unlockStatus = {
        state: 'UNLOCK_PENDING',
        remainingMs: 0,
        canConfirm: true,
      };
      return this.unlockStatus;
    }

    async getUnlockStatus() {
      return this.unlockStatus;
    }

    async confirmUnlock() {
      this.config.lockState = 'UNLOCKED';
      this.unlockStatus = { state: 'UNLOCKED' };
    }

    async cancelUnlockCountdown() {
      this.unlockStatus = { state: 'LOCKED' };
      this.config.lockState = 'LOCKED';
    }
  }

  const nativeModule = new FakeNativeModule();
  const controller = new FocusGateController({
    nativeModule,
    now: () => new Date('2026-06-22T18:00:00+02:00'),
    idFactory: () => 'rule-1',
  });

  let state = await controller.refresh();
  assert.equal(state.dashboard.warning, 'Filtering inactive');
  assert.equal(state.lock.action, 'Start unlocking');
  assert.equal(state.diagnostics.vpnActive, 'No');

  state = await controller.startUnlockCountdown();
  assert.equal(state.lock.action, 'Unlock editing');
  state = await controller.cancelUnlockCountdown();
  assert.equal(state.lock.action, 'Start unlocking');

  state = await controller.startUnlockCountdown();
  state = await controller.confirmUnlock();
  assert.equal(state.lock.action, 'Lock editing');

  state = controller.beginAddRule();
  assert.equal(state.editor.domain, '');
  state = controller.updateDraft({ domain: 'facebook.com' });
  assert.equal(state.editor.domain, 'facebook.com');
  state = controller.toggleDraftSlot(0, 19);
  assert.equal(state.editor.weeklySlots[getSlotIndex(0, 19)], true);
  state = controller.applyDraftPreset('WEEKDAYS');
  assert.equal(state.editor.weeklySlots[getSlotIndex(2, 12)], true);
  state = controller.toggleDraftHour(8);
  assert.equal(state.editor.weeklySlots[getSlotIndex(6, 8)], true);
  state = controller.toggleDraftDay(6);
  assert.equal(state.editor.weeklySlots[getSlotIndex(6, 22)], true);
  controller.copyDraftDay(0);
  state = controller.pasteDraftDay(5);
  assert.equal(
    state.editor.weeklySlots[getSlotIndex(5, 12)],
    state.editor.weeklySlots[getSlotIndex(0, 12)],
  );
  state = controller.dragSelectDraftSlots(0, 18, 0, 20, true);
  assert.equal(state.editor.weeklySlots[getSlotIndex(0, 20)], true);
  state = await controller.saveDraft();
  assert.equal(state.editor, null);
  assert.equal(state.domains.length, 1);
  assert.equal(state.lock.action, 'Start unlocking');

  state = await controller.startVpn();
  assert.equal(state.dashboard.vpnStatus, 'RUNNING');
  assert.equal(state.diagnostics.dnsInterception, 'Working');
  assert.equal(state.diagnostics.networkAvailable, true);
  state = await controller.startUnlockCountdown();
  state = await controller.confirmUnlock();
  state = await controller.updateVpnConfig({
    upstreamDns: { ip: '8.8.8.8', port: 53 },
    filteredApplications: ['com.brave.browser'],
  });
  assert.equal(state.vpnConfig.upstreamDns.ip, '8.8.8.8');
  assert.equal(state.onboarding.upstreamDnsSummary, '8.8.8.8:53');

  state = controller.beginEditRule('rule-1');
  state = controller.updateDraft({ matchMode: MatchMode.EXACT });
  assert.equal(state.editor.matchMode, MatchMode.EXACT);
  state = await controller.startUnlockCountdown();
  state = await controller.confirmUnlock();
  state = await controller.deleteRule('rule-1');
  assert.equal(state.domains.length, 0);
  assert.equal(state.editor, null);
  const exported = await controller.exportConfig();
  assert.equal(exported.upstreamDns.ip, '8.8.8.8');
  state = await controller.startUnlockCountdown();
  state = await controller.confirmUnlock();
  state = await controller.importConfig({
    ...exported,
    rules: [
      {
        id: 'rule-imported',
        domain: 'reddit.com',
        enabled: true,
        matchMode: MatchMode.DOMAIN_AND_SUBDOMAINS,
        scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
        weeklySlots: createWeeklySlots(false),
      },
    ],
    filteredApplications: ['com.brave.browser'],
    upstreamDns: { ip: '9.9.9.9', port: 53 },
  });
  assert.equal(state.domains[0].domain, 'reddit.com');
  assert.equal(state.vpnConfig.upstreamDns.ip, '9.9.9.9');
  state = await controller.startUnlockCountdown();
  state = await controller.confirmUnlock();
  state = await controller.resetConfig();
  assert.equal(state.domains.length, 0);
  assert.equal(state.vpnConfig.upstreamDns.ip, '1.1.1.1');

  await controller.stopVpn();
  state = controller.cancelEditor();
  assert.equal(state.dashboard.vpnStatus, 'STOPPED');
});

test('local native module provides runnable controller backend', async () => {
  let elapsed = 1_000;
  const nativeModule = new LocalFocusGateNativeModule({
    elapsedRealtimeMs: () => elapsed,
    bootCount: () => 4,
  });
  const controller = new FocusGateController({
    nativeModule,
    now: () => new Date('2026-06-22T18:00:00+02:00'),
    idFactory: () => 'rule-2',
  });

  await controller.refresh();
  await controller.startUnlockCountdown();
  let countdownState = await controller.cancelUnlockCountdown();
  assert.equal(countdownState.lock.title, 'Editing locked');
  await controller.startUnlockCountdown();
  elapsed = 301_000;
  await controller.confirmUnlock();
  controller.beginAddRule();
  controller.updateDraft({ domain: 'reddit.com' });
  const state = await controller.saveDraft();

  assert.equal(state.domains[0].domain, 'reddit.com');
  assert.equal(state.lock.title, 'Editing locked');
  assert.equal(state.onboarding.braveStatus, 'Brave configured for filtering');
  assert.equal(state.vpnConfig.upstreamDns.ip, '1.1.1.1');
});

test('controller toggles rule enabled state through native updates', async () => {
  class FakeNativeModule {
    constructor() {
      this.config = {
        rules: [
          {
            id: 'rule-1',
            domain: 'facebook.com',
            enabled: true,
            matchMode: MatchMode.DOMAIN_AND_SUBDOMAINS,
            scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
            weeklySlots: createWeeklySlots(false),
          },
        ],
        lockState: 'UNLOCKED',
        vpnStatus: 'RUNNING',
        filteredApplications: ['com.brave.browser'],
        upstreamDns: { ip: '1.1.1.1', port: 53 },
      };
    }

    async getConfig() {
      return {
        rules: this.config.rules.map((rule) => ({ ...rule, weeklySlots: [...rule.weeklySlots] })),
        lockState: this.config.lockState,
        vpnStatus: this.config.vpnStatus,
        filteredApplications: [...this.config.filteredApplications],
        upstreamDns: { ...this.config.upstreamDns },
      };
    }

    async getUnlockStatus() {
      return { state: this.config.lockState };
    }

    async getVpnStatus() {
      return this.config.vpnStatus;
    }

    async getDiagnostics() {
      return null;
    }

    async updateRule(rule) {
      this.config.rules = this.config.rules.map((entry) =>
        entry.id === rule.id ? { ...rule, weeklySlots: [...rule.weeklySlots] } : entry,
      );
    }
  }

  const controller = new FocusGateController({ nativeModule: new FakeNativeModule() });
  let state = await controller.refresh();
  assert.equal(state.domains[0].enabled, true);

  state = await controller.toggleRuleEnabled('rule-1');
  assert.equal(state.domains[0].enabled, false);
  assert.equal(controller.config.rules[0].enabled, false);
});

test('screen renderer outputs dashboard, domain list, editor, and lock screens', async () => {
  let elapsed = 1_000;
  const nativeModule = new LocalFocusGateNativeModule({
    elapsedRealtimeMs: () => elapsed,
    bootCount: () => 2,
  });
  const controller = new FocusGateController({
    nativeModule,
    now: () => new Date('2026-06-22T18:00:00+02:00'),
    idFactory: () => 'rule-3',
  });

  await controller.refresh();
  await controller.startUnlockCountdown();
  elapsed = 301_000;
  await controller.confirmUnlock();
  controller.beginAddRule();
  controller.updateDraft({ domain: 'facebook.com' });
  controller.applyDraftPreset('WEEKDAYS');
  const editingScreens = renderAppScreens(controller.getState());
  assert.match(editingScreens.editor, /facebook\.com/);
  assert.match(editingScreens.lock, /Lock editing/);

  await controller.saveDraft();
  await controller.startVpn();
  const screens = renderAppScreens(controller.getState());
  assert.match(screens.dashboard, /FocusGate/);
  assert.match(screens.dashboard, /RUNNING/);
  assert.match(screens.domains, /facebook\.com/);
  assert.match(screens.domains, /Allowed now/);
  assert.match(screens.diagnostics, /DNS interception: Working/);
  assert.match(screens.onboarding, /Disable Secure DNS in Brave/);
  assert.match(screens.onboarding, /Upstream DNS: 1\.1\.1\.1:53/);
});

test('runtime native module prefers explicit or turbo-backed native implementation with fallback', async () => {
  const fallbackModule = new LocalFocusGateNativeModule();
  const explicitModule = {
    async getConfig() {
      return { rules: [], lockState: 'LOCKED', vpnStatus: 'STOPPED' };
    },
  };

  assert.equal(
    resolveNativeModule({ nativeModule: explicitModule, fallbackModule }),
    explicitModule,
  );

  globalThis.__turboModuleProxy = (name) =>
    name === 'FocusGateNative'
      ? {
          async getConfig() {
            return { rules: [], lockState: 'LOCKED', vpnStatus: 'STOPPED' };
          },
          async addRule() {},
          async updateRule() {},
          async deleteRule() {},
          async exportConfig() {
            return { rules: [], lockState: 'LOCKED', vpnStatus: 'STOPPED' };
          },
          async importConfig() {},
          async resetConfig() {},
          async updateVpnConfig() {},
          async startVpn() {},
          async stopVpn() {},
          async getVpnStatus() {
            return 'STOPPED';
          },
          async getDiagnostics() {
            return {
              vpnActive: false,
              dnsInterception: 'Inactive',
              braveFilteringTest: 'Not configured',
              currentlyBlocked: 0,
              restartRequired: false,
              restartReason: null,
              networkAvailable: true,
            };
          },
          async enableEditLock() {},
          async startUnlockCountdown() {
            return { state: 'LOCKED' };
          },
          async getUnlockStatus() {
            return { state: 'LOCKED' };
          },
          async confirmUnlock() {},
          async cancelUnlockCountdown() {},
        }
      : null;

  const runtimeModule = new RuntimeFocusGateNativeModule({ fallbackModule });
  const config = await runtimeModule.getConfig();
  assert.deepEqual(config, { rules: [], lockState: 'LOCKED', vpnStatus: 'STOPPED' });

  delete globalThis.__turboModuleProxy;
  const resolvedFallback = resolveNativeModule({ fallbackModule });
  assert.equal(resolvedFallback, fallbackModule);
});
