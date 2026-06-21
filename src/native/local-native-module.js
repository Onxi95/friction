import { createLockedState, createUnlockedState, getUnlockStatus, startUnlockCountdown, confirmUnlock } from '../lock/lock.js';
import { ConfigStore } from '../state/config-store.js';
import { buildDiagnosticsViewModel } from '../ui/view-models.js';

export class LocalFocusGateNativeModule {
  constructor({
    store = new ConfigStore({ lockState: createLockedState() }),
    elapsedRealtimeMs = () => Date.now(),
    bootCount = () => 1,
  } = {}) {
    this.store = store;
    this.elapsedRealtimeMs = elapsedRealtimeMs;
    this.bootCount = bootCount;
    this.unlockState = { type: 'LOCKED' };
  }

  async getConfig() {
    return this.store.getConfig();
  }

  async addRule(rule) {
    this.store.lockState = this.unlockState.type === 'UNLOCKED' ? createUnlockedState() : this.unlockState;
    const created = this.store.addRule(rule);
    this.unlockState = createLockedState();
    return created;
  }

  async updateRule(rule) {
    this.store.lockState = this.unlockState.type === 'UNLOCKED' ? createUnlockedState() : this.unlockState;
    const updated = this.store.updateRule(rule);
    this.unlockState = createLockedState();
    return updated;
  }

  async deleteRule(ruleId) {
    this.store.lockState = this.unlockState.type === 'UNLOCKED' ? createUnlockedState() : this.unlockState;
    this.store.deleteRule(ruleId);
    this.unlockState = createLockedState();
  }

  async exportConfig() {
    return this.store.exportConfig();
  }

  async importConfig(config) {
    this.store.lockState = this.unlockState.type === 'UNLOCKED' ? createUnlockedState() : this.unlockState;
    const updated = this.store.importConfig(config);
    this.unlockState = createLockedState();
    return updated;
  }

  async resetConfig() {
    this.store.lockState = this.unlockState.type === 'UNLOCKED' ? createUnlockedState() : this.unlockState;
    const updated = this.store.resetConfig();
    this.unlockState = createLockedState();
    return updated;
  }

  async startVpn() {
    this.store.vpnStatus = 'RUNNING';
  }

  async stopVpn() {
    this.store.vpnStatus = 'STOPPED';
  }

  async getVpnStatus() {
    return this.store.vpnStatus;
  }

  async getDiagnostics() {
    return buildDiagnosticsViewModel(this.store.getConfig(), new Date());
  }

  async enableEditLock() {
    this.unlockState = createLockedState();
    this.store.lockState = this.unlockState;
  }

  async startUnlockCountdown() {
    this.unlockState = startUnlockCountdown(this.elapsedRealtimeMs(), this.bootCount());
    this.store.lockState = this.unlockState;
    return this.getUnlockStatus();
  }

  async getUnlockStatus() {
    return getUnlockStatus(this.unlockState, this.elapsedRealtimeMs(), this.bootCount());
  }

  async confirmUnlock() {
    this.unlockState = confirmUnlock(this.unlockState, this.elapsedRealtimeMs(), this.bootCount());
    this.store.lockState = this.unlockState;
  }

  async cancelUnlockCountdown() {
    this.unlockState = createLockedState();
    this.store.lockState = this.unlockState;
  }

  async updateVpnConfig(config) {
    this.store.lockState = this.unlockState.type === 'UNLOCKED' ? createUnlockedState() : this.unlockState;
    const updated = this.store.updateVpnConfig(config);
    this.unlockState = createLockedState();
    return updated;
  }
}
