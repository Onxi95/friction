import { createWeeklySlots, MatchMode, ScheduleMode } from '../domain/model.js';
import {
  applyDragSelection,
  applyPreset,
  copyDay,
  pasteDay,
  summarizeSchedule,
  toggleDay,
  toggleHour,
  toggleSlot,
} from '../domain/schedule.js';
import {
  buildDashboardViewModel,
  buildDiagnosticsViewModel,
  buildDomainListViewModel,
  buildLockViewModel,
  buildOnboardingViewModel,
} from '../ui/view-models.js';

function normalizeLockState(lockState) {
  if (typeof lockState === 'string') {
    return { type: lockState };
  }

  return lockState;
}

function normalizeConfig(config) {
  return {
    ...config,
    lockState: normalizeLockState(config.lockState),
    filteredApplications: config.filteredApplications ?? ['com.brave.browser'],
  };
}

function createEmptyDraft(now = Date.now()) {
  return {
    id: `rule-${now}`,
    domain: '',
    enabled: true,
    matchMode: MatchMode.DOMAIN_AND_SUBDOMAINS,
    scheduleMode: ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
    weeklySlots: createWeeklySlots(false),
  };
}

function createEditorViewModel(draft) {
  return {
    id: draft.id,
    domain: draft.domain,
    enabled: draft.enabled,
    matchMode: draft.matchMode,
    scheduleMode: draft.scheduleMode,
    weeklySlots: [...draft.weeklySlots],
    summary: summarizeSchedule(
      draft.domain || 'This domain',
      draft.weeklySlots,
      draft.scheduleMode,
    ),
  };
}

export class FocusGateController {
  constructor({
    nativeModule,
    now = () => new Date(),
    idFactory = () => `rule-${Date.now()}`,
  }) {
    this.nativeModule = nativeModule;
    this.now = now;
    this.idFactory = idFactory;
    this.config = normalizeConfig({
      rules: [],
      lockState: 'LOCKED',
      vpnStatus: 'STOPPED',
    });
    this.unlockStatus = { state: 'LOCKED' };
    this.nativeDiagnostics = null;
    this.editorDraft = null;
    this.copiedDaySlots = null;
  }

  async refresh() {
    const [config, unlockStatus, vpnStatus, diagnostics] = await Promise.all([
      this.nativeModule.getConfig(),
      this.nativeModule.getUnlockStatus(),
      this.nativeModule.getVpnStatus(),
      typeof this.nativeModule.getDiagnostics === 'function'
        ? this.nativeModule.getDiagnostics()
        : Promise.resolve(null),
    ]);

    this.config = normalizeConfig({
      ...config,
      vpnStatus,
    });
    this.unlockStatus = unlockStatus;
    this.nativeDiagnostics = diagnostics;
    return this.getState();
  }

  getState() {
    const now = this.now();

    return {
      dashboard: buildDashboardViewModel(this.config, now),
      domains: buildDomainListViewModel(this.config, now),
      lock: buildLockViewModel(this.unlockStatus),
      editor: this.editorDraft ? createEditorViewModel(this.editorDraft) : null,
      diagnostics: this.nativeDiagnostics ?? buildDiagnosticsViewModel(this.config, now),
      onboarding: buildOnboardingViewModel(this.config),
      vpnConfig: {
        filteredApplications: [...this.config.filteredApplications],
        upstreamDns: { ...(this.config.upstreamDns ?? { ip: '1.1.1.1', port: 53 }) },
      },
    };
  }

  beginAddRule() {
    this.editorDraft = {
      ...createEmptyDraft(),
      id: this.idFactory(),
    };
    return this.getState();
  }

  beginEditRule(ruleId) {
    const rule = this.config.rules.find((entry) => entry.id === ruleId);

    if (!rule) {
      throw new Error('RULE_NOT_FOUND');
    }

    this.editorDraft = {
      ...rule,
      weeklySlots: [...rule.weeklySlots],
    };
    return this.getState();
  }

  updateDraft(changes) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      ...changes,
    };
    return this.getState();
  }

  toggleDraftSlot(dayIndex, hour) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      weeklySlots: toggleSlot(this.editorDraft.weeklySlots, dayIndex, hour),
    };
    return this.getState();
  }

  applyDraftPreset(preset) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      weeklySlots: applyPreset(this.editorDraft.weeklySlots, preset),
    };
    return this.getState();
  }

  dragSelectDraftSlots(startDayIndex, startHour, endDayIndex, endHour, selected) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      weeklySlots: applyDragSelection(
        this.editorDraft.weeklySlots,
        startDayIndex,
        startHour,
        endDayIndex,
        endHour,
        selected,
      ),
    };
    return this.getState();
  }

  toggleDraftDay(dayIndex) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      weeklySlots: toggleDay(this.editorDraft.weeklySlots, dayIndex),
    };
    return this.getState();
  }

  toggleDraftHour(hour) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      weeklySlots: toggleHour(this.editorDraft.weeklySlots, hour),
    };
    return this.getState();
  }

  copyDraftDay(dayIndex) {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.copiedDaySlots = copyDay(this.editorDraft.weeklySlots, dayIndex);
    return this.getState();
  }

  pasteDraftDay(dayIndex) {
    if (!this.editorDraft || !this.copiedDaySlots) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    this.editorDraft = {
      ...this.editorDraft,
      weeklySlots: pasteDay(this.editorDraft.weeklySlots, dayIndex, this.copiedDaySlots),
    };
    return this.getState();
  }

  async saveDraft() {
    if (!this.editorDraft) {
      throw new Error('EDITOR_NOT_OPEN');
    }

    const existing = this.config.rules.some((rule) => rule.id === this.editorDraft.id);

    if (existing) {
      await this.nativeModule.updateRule(this.editorDraft);
    } else {
      await this.nativeModule.addRule(this.editorDraft);
    }

    this.editorDraft = null;
    return this.refresh();
  }

  async deleteRule(ruleId) {
    await this.nativeModule.deleteRule(ruleId);

    if (this.editorDraft?.id === ruleId) {
      this.editorDraft = null;
    }

    return this.refresh();
  }

  async toggleRuleEnabled(ruleId) {
    const rule = this.config.rules.find((entry) => entry.id === ruleId);

    if (!rule) {
      throw new Error('RULE_NOT_FOUND');
    }

    await this.nativeModule.updateRule({
      ...rule,
      enabled: !rule.enabled,
      weeklySlots: [...rule.weeklySlots],
    });

    return this.refresh();
  }

  async exportConfig() {
    return this.nativeModule.exportConfig();
  }

  async importConfig(config) {
    await this.nativeModule.importConfig(config);
    this.editorDraft = null;
    return this.refresh();
  }

  async resetConfig() {
    await this.nativeModule.resetConfig();
    this.editorDraft = null;
    return this.refresh();
  }

  cancelEditor() {
    this.editorDraft = null;
    return this.getState();
  }

  async startUnlockCountdown() {
    this.unlockStatus = await this.nativeModule.startUnlockCountdown();
    return this.refresh();
  }

  async confirmUnlock() {
    await this.nativeModule.confirmUnlock();
    return this.refresh();
  }

  async cancelUnlockCountdown() {
    await this.nativeModule.cancelUnlockCountdown();
    return this.refresh();
  }

  async lockEditing() {
    await this.nativeModule.enableEditLock();
    return this.refresh();
  }

  async startVpn() {
    await this.nativeModule.startVpn();
    return this.refresh();
  }

  async stopVpn() {
    await this.nativeModule.stopVpn();
    return this.refresh();
  }

  async updateVpnConfig(config) {
    await this.nativeModule.updateVpnConfig(config);
    return this.refresh();
  }
}
