import React, { useEffect, useState } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { FocusGateController } from './src/app/focusgate-controller';
import { RuntimeFocusGateNativeModule } from './src/native/runtime-native-module';
import { DashboardCard, DomainListCard, EditorCard, LockCard, DiagnosticsCard, OnboardingCard } from './src/components/screens';

const controller = new FocusGateController({
  nativeModule: new RuntimeFocusGateNativeModule(),
});

export default function App() {
  const [state, setState] = useState(controller.getState());
  const [error, setError] = useState<string | null>(null);
  const [exportSummary, setExportSummary] = useState<string | null>(null);

  useEffect(() => {
    controller.refresh().then(setState);
  }, []);

  const refreshState = async (task) => {
    try {
      const nextState = await task();
      setState(nextState);
      setError(null);
    } catch (taskError) {
      const message =
        taskError instanceof Error ? taskError.message : 'Unexpected runtime error';
      setError(message);
    }
  };

  const addSampleRule = async () => {
    controller.beginAddRule();
    controller.updateDraft({ domain: 'facebook.com' });
    controller.applyDraftPreset('WEEKDAYS');
    await refreshState(() => controller.saveDraft());
  };

  const deleteFirstRule = async () => {
    const firstRule = state.domains[0];
    if (!firstRule) {
      setError('RULE_NOT_FOUND');
      return;
    }

    await refreshState(() => controller.deleteRule(firstRule.id));
  };

  const exportCurrentConfig = async () => {
    try {
      const exported = await controller.exportConfig();
      setExportSummary(
        `${exported.rules.length} rules · DNS ${exported.upstreamDns.ip}:${exported.upstreamDns.port}`,
      );
      setError(null);
    } catch (taskError) {
      const message =
        taskError instanceof Error ? taskError.message : 'Unexpected runtime error';
      setError(message);
    }
  };

  const importSampleConfig = async () => {
    await refreshState(() =>
      controller.importConfig({
        rules: [
          {
            id: 'sample-reddit',
            domain: 'reddit.com',
            enabled: true,
            matchMode: 'DOMAIN_AND_SUBDOMAINS',
            scheduleMode: 'ALLOW_ONLY_DURING_SELECTED_HOURS',
            weeklySlots: Array.from({ length: 168 }, (_, index) => Math.floor(index / 24) <= 4),
          },
        ],
        lockState: 'LOCKED',
        vpnStatus: state.dashboard.vpnStatus,
        filteredApplications: ['com.brave.browser'],
        upstreamDns: { ip: '9.9.9.9', port: 53 },
      }),
    );
  };

  const resetConfig = async () => {
    await refreshState(() => controller.resetConfig());
    setExportSummary(null);
  };

  const beginAddRule = () => {
    setState(controller.beginAddRule());
    setError(null);
  };

  const beginEditRule = (ruleId) => {
    try {
      setState(controller.beginEditRule(ruleId));
      setError(null);
    } catch (taskError) {
      const message =
        taskError instanceof Error ? taskError.message : 'Unexpected runtime error';
      setError(message);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" />
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>FocusGate</Text>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        {exportSummary ? <Text style={styles.summaryBanner}>Exported: {exportSummary}</Text> : null}
        <DashboardCard state={state.dashboard} />
        <View style={styles.actions}>
          <TouchableOpacity style={styles.button} onPress={() => refreshState(() => controller.startVpn())}>
            <Text style={styles.buttonText}>Start VPN</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={() => refreshState(() => controller.stopVpn())}>
            <Text style={styles.buttonTextSecondary}>Stop VPN</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={() => refreshState(() => controller.startUnlockCountdown())}>
            <Text style={styles.buttonTextSecondary}>Unlock</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={() => refreshState(() => controller.cancelUnlockCountdown())}>
            <Text style={styles.buttonTextSecondary}>Cancel unlock</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={() => refreshState(() => controller.confirmUnlock())}>
            <Text style={styles.buttonTextSecondary}>Confirm unlock</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={() => refreshState(() => controller.lockEditing())}>
            <Text style={styles.buttonTextSecondary}>Lock editing</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.buttonSecondary}
            onPress={() =>
              refreshState(() =>
                controller.updateVpnConfig({
                  upstreamDns: { ip: '8.8.8.8', port: 53 },
                  filteredApplications: ['com.brave.browser'],
                }),
              )
            }>
            <Text style={styles.buttonTextSecondary}>Use 8.8.8.8</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={addSampleRule}>
            <Text style={styles.buttonTextSecondary}>Add sample rule</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={deleteFirstRule}>
            <Text style={styles.buttonTextSecondary}>Delete first rule</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={exportCurrentConfig}>
            <Text style={styles.buttonTextSecondary}>Export config</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={importSampleConfig}>
            <Text style={styles.buttonTextSecondary}>Import sample</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.buttonSecondary} onPress={resetConfig}>
            <Text style={styles.buttonTextSecondary}>Reset config</Text>
          </TouchableOpacity>
        </View>
        <DomainListCard
          state={state.domains}
          onAdd={beginAddRule}
          onEdit={beginEditRule}
          onToggleEnabled={(ruleId) => refreshState(() => controller.toggleRuleEnabled(ruleId))}
        />
        <EditorCard
          state={state.editor}
          onDomainChange={(domain) => setState(controller.updateDraft({ domain }))}
          onToggleEnabled={() =>
            setState(controller.updateDraft({ enabled: !state.editor?.enabled }))
          }
          onSetMatchMode={(matchMode) => setState(controller.updateDraft({ matchMode }))}
          onSetScheduleMode={(scheduleMode) => setState(controller.updateDraft({ scheduleMode }))}
          onToggleSlot={(dayIndex, hour) => setState(controller.toggleDraftSlot(dayIndex, hour))}
          onToggleDay={(dayIndex) => setState(controller.toggleDraftDay(dayIndex))}
          onToggleHour={(hour) => setState(controller.toggleDraftHour(hour))}
          onApplyPreset={(preset) => setState(controller.applyDraftPreset(preset))}
          onCopyDay={(dayIndex) => setState(controller.copyDraftDay(dayIndex))}
          onPasteDay={(dayIndex) => setState(controller.pasteDraftDay(dayIndex))}
          onSave={() => refreshState(() => controller.saveDraft())}
          onCancel={() => setState(controller.cancelEditor())}
        />
        <LockCard state={state.lock} />
        <DiagnosticsCard state={state.diagnostics} />
        <OnboardingCard state={state.onboarding} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#13211D',
  },
  container: {
    padding: 20,
    gap: 16,
  },
  title: {
    color: '#F7F1E3',
    fontSize: 32,
    fontWeight: '700',
    letterSpacing: 0.4,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  button: {
    backgroundColor: '#D9A441',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  buttonSecondary: {
    backgroundColor: '#F7F1E3',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  buttonText: {
    color: '#13211D',
    fontWeight: '700',
  },
  buttonTextSecondary: {
    color: '#13211D',
    fontWeight: '600',
  },
  error: {
    color: '#FFD6C9',
    backgroundColor: '#7A2E1F',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 12,
    fontWeight: '700',
  },
  summaryBanner: {
    color: '#13211D',
    backgroundColor: '#D9A441',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 12,
    fontWeight: '700',
  },
});
