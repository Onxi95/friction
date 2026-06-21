import { FocusGateNativeModule } from './module.js';
import { LocalFocusGateNativeModule } from './local-native-module.js';

function getTurboModule() {
  if (typeof globalThis.__turboModuleProxy === 'function') {
    return globalThis.__turboModuleProxy('FocusGateNative');
  }

  return null;
}

export function resolveNativeModule({
  nativeModule,
  fallbackModule = new LocalFocusGateNativeModule(),
} = {}) {
  return nativeModule ?? globalThis.__FOCUSGATE_NATIVE__ ?? getTurboModule() ?? fallbackModule;
}

export class RuntimeFocusGateNativeModule extends FocusGateNativeModule {
  constructor(options = {}) {
    super();
    this.delegate = resolveNativeModule(options);
  }

  async getConfig() {
    return this.delegate.getConfig();
  }

  async addRule(rule) {
    return this.delegate.addRule(rule);
  }

  async updateRule(rule) {
    return this.delegate.updateRule(rule);
  }

  async deleteRule(ruleId) {
    return this.delegate.deleteRule(ruleId);
  }

  async exportConfig() {
    return this.delegate.exportConfig();
  }

  async importConfig(config) {
    return this.delegate.importConfig(config);
  }

  async resetConfig() {
    return this.delegate.resetConfig();
  }

  async startVpn() {
    return this.delegate.startVpn();
  }

  async stopVpn() {
    return this.delegate.stopVpn();
  }

  async updateVpnConfig(config) {
    return this.delegate.updateVpnConfig(config);
  }

  async getVpnStatus() {
    return this.delegate.getVpnStatus();
  }

  async getDiagnostics() {
    if (typeof this.delegate.getDiagnostics === 'function') {
      return this.delegate.getDiagnostics();
    }

    return null;
  }

  async enableEditLock() {
    return this.delegate.enableEditLock();
  }

  async startUnlockCountdown() {
    return this.delegate.startUnlockCountdown();
  }

  async getUnlockStatus() {
    return this.delegate.getUnlockStatus();
  }

  async confirmUnlock() {
    return this.delegate.confirmUnlock();
  }

  async cancelUnlockCountdown() {
    return this.delegate.cancelUnlockCountdown();
  }
}
