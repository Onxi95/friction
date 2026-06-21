import { assertUniqueDomain, normalizeDomain } from '../domain/normalizer.js';
import { createDomainRule } from '../domain/model.js';
import { requireEditingUnlocked, relockAfterWrite } from '../lock/lock.js';

export class ConfigStore {
  constructor({
    lockState,
    rules = [],
    vpnStatus = 'STOPPED',
    filteredApplications = ['com.brave.browser'],
    upstreamDns = {
      ip: '1.1.1.1',
      port: 53,
    },
  } = {}) {
    this.lockState = lockState;
    this.rules = [...rules];
    this.vpnStatus = vpnStatus;
    this.filteredApplications = [...filteredApplications];
    this.upstreamDns = { ...upstreamDns };
  }

  getConfig() {
    return {
      rules: this.rules.map((rule) => ({ ...rule, weeklySlots: [...rule.weeklySlots] })),
      lockState: this.lockState,
      vpnStatus: this.vpnStatus,
      filteredApplications: [...this.filteredApplications],
      upstreamDns: { ...this.upstreamDns },
    };
  }

  exportConfig() {
    return this.getConfig();
  }

  addRule(ruleInput) {
    requireEditingUnlocked(this.lockState);
    const domain = normalizeDomain(ruleInput.domain);
    assertUniqueDomain(
      domain,
      this.rules.map((rule) => rule.domain),
    );
    const rule = createDomainRule({ ...ruleInput, domain });
    this.rules = [...this.rules, rule];
    this.lockState = relockAfterWrite();
    return rule;
  }

  updateRule(ruleInput) {
    requireEditingUnlocked(this.lockState);
    const domain = normalizeDomain(ruleInput.domain);
    const remaining = this.rules.filter((rule) => rule.id !== ruleInput.id);
    assertUniqueDomain(
      domain,
      remaining.map((rule) => rule.domain),
    );
    const rule = createDomainRule({ ...ruleInput, domain });
    this.rules = [...remaining, rule];
    this.lockState = relockAfterWrite();
    return rule;
  }

  deleteRule(ruleId) {
    requireEditingUnlocked(this.lockState);
    this.rules = this.rules.filter((rule) => rule.id !== ruleId);
    this.lockState = relockAfterWrite();
  }

  updateVpnConfig({ filteredApplications, upstreamDns }) {
    requireEditingUnlocked(this.lockState);

    if (filteredApplications) {
      if (!Array.isArray(filteredApplications) || filteredApplications.length === 0) {
        throw new Error('INVALID_FILTERED_APPLICATIONS');
      }

      this.filteredApplications = [...filteredApplications];
    }

    if (upstreamDns) {
      const port = Number(upstreamDns.port);

      if (!upstreamDns.ip || Number.isNaN(port) || port < 1 || port > 65535) {
        throw new Error('INVALID_UPSTREAM_DNS');
      }

      this.upstreamDns = {
        ip: upstreamDns.ip,
        port,
      };
    }

    this.lockState = relockAfterWrite();
    return this.getConfig();
  }

  importConfig(config) {
    requireEditingUnlocked(this.lockState);

    const rules =
      (config.rules ?? []).map((rule) =>
        createDomainRule({
          ...rule,
          domain: normalizeDomain(rule.domain),
          weeklySlots: [...rule.weeklySlots],
        }),
      );

    const filteredApplications = config.filteredApplications ?? ['com.brave.browser'];
    if (!Array.isArray(filteredApplications) || filteredApplications.length === 0) {
      throw new Error('INVALID_FILTERED_APPLICATIONS');
    }

    const upstreamDns = config.upstreamDns ?? { ip: '1.1.1.1', port: 53 };
    const port = Number(upstreamDns.port);
    if (!upstreamDns.ip || Number.isNaN(port) || port < 1 || port > 65535) {
      throw new Error('INVALID_UPSTREAM_DNS');
    }

    this.rules = rules;
    this.filteredApplications = [...filteredApplications];
    this.upstreamDns = {
      ip: upstreamDns.ip,
      port,
    };
    this.lockState = relockAfterWrite();
    return this.getConfig();
  }

  resetConfig() {
    requireEditingUnlocked(this.lockState);
    this.rules = [];
    this.filteredApplications = ['com.brave.browser'];
    this.upstreamDns = {
      ip: '1.1.1.1',
      port: 53,
    };
    this.lockState = relockAfterWrite();
    return this.getConfig();
  }
}
