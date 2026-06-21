import React from 'react';
import { ScrollView, StyleSheet, Switch, Text, TextInput, TouchableOpacity, View } from 'react-native';

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const HOURS = Array.from({ length: 24 }, (_, hour) => hour);

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>{title}</Text>
      {children}
    </View>
  );
}

function ModeButton({ active, label, onPress }) {
  return (
    <TouchableOpacity
      style={[styles.modeButton, active ? styles.modeButtonActive : null]}
      onPress={onPress}>
      <Text style={[styles.modeButtonText, active ? styles.modeButtonTextActive : null]}>{label}</Text>
    </TouchableOpacity>
  );
}

function PresetButton({ label, onPress }) {
  return (
    <TouchableOpacity style={styles.presetButton} onPress={onPress}>
      <Text style={styles.presetButtonText}>{label}</Text>
    </TouchableOpacity>
  );
}

export function DashboardCard({ state }) {
  return (
    <Card title="Dashboard">
      <Text style={styles.line}>VPN: {state.vpnStatus}</Text>
      <Text style={styles.line}>Editing: {state.lockState}</Text>
      <Text style={styles.line}>Active rules: {state.activeRules}</Text>
      <Text style={styles.line}>Blocked now: {state.blockedCount}</Text>
      {state.warning ? <Text style={styles.warning}>{state.warning}</Text> : null}
    </Card>
  );
}

export function DomainListCard({ state, onAdd, onEdit, onToggleEnabled }) {
  return (
    <Card title="Domains">
      {state.length === 0 ? <Text style={styles.line}>No domains configured</Text> : null}
      {state.map((rule) => (
        <View key={rule.id} style={styles.rule}>
          <Text style={styles.ruleDomain}>{rule.domain}</Text>
          <Text style={styles.line}>{rule.status}</Text>
          <Text style={styles.summary}>{rule.summary}</Text>
          <View style={styles.ruleActions}>
            <View style={styles.switchRow}>
              <Text style={styles.smallLabel}>Enabled</Text>
              <Switch value={rule.enabled} onValueChange={() => onToggleEnabled?.(rule.id)} />
            </View>
            <TouchableOpacity style={styles.inlineButton} onPress={() => onEdit?.(rule.id)}>
              <Text style={styles.inlineButtonText}>Edit</Text>
            </TouchableOpacity>
          </View>
        </View>
      ))}
      <TouchableOpacity style={styles.primaryButton} onPress={onAdd}>
        <Text style={styles.primaryButtonText}>Add domain</Text>
      </TouchableOpacity>
    </Card>
  );
}

export function EditorCard({
  state,
  onDomainChange,
  onToggleEnabled,
  onSetMatchMode,
  onSetScheduleMode,
  onToggleSlot,
  onToggleDay,
  onToggleHour,
  onApplyPreset,
  onCopyDay,
  onPasteDay,
  onSave,
  onCancel,
}) {
  return (
    <Card title="Editor">
      {state ? (
        <>
          <Text style={styles.smallLabel}>Domain</Text>
          <TextInput
            style={styles.input}
            value={state.domain}
            placeholder="facebook.com"
            placeholderTextColor="#6E7D77"
            autoCapitalize="none"
            autoCorrect={false}
            onChangeText={onDomainChange}
          />
          <View style={styles.switchRow}>
            <Text style={styles.line}>Rule enabled</Text>
            <Switch value={state.enabled} onValueChange={onToggleEnabled} />
          </View>
          <Text style={styles.smallLabel}>Match mode</Text>
          <View style={styles.modeRow}>
            <ModeButton
              active={state.matchMode === 'DOMAIN_AND_SUBDOMAINS'}
              label="Domain + subdomains"
              onPress={() => onSetMatchMode?.('DOMAIN_AND_SUBDOMAINS')}
            />
            <ModeButton
              active={state.matchMode === 'EXACT'}
              label="Exact domain"
              onPress={() => onSetMatchMode?.('EXACT')}
            />
          </View>
          <Text style={styles.smallLabel}>Schedule mode</Text>
          <View style={styles.modeRow}>
            <ModeButton
              active={state.scheduleMode === 'ALLOW_ONLY_DURING_SELECTED_HOURS'}
              label="Allow selected"
              onPress={() => onSetScheduleMode?.('ALLOW_ONLY_DURING_SELECTED_HOURS')}
            />
            <ModeButton
              active={state.scheduleMode === 'BLOCK_DURING_SELECTED_HOURS'}
              label="Block selected"
              onPress={() => onSetScheduleMode?.('BLOCK_DURING_SELECTED_HOURS')}
            />
          </View>
          <Text style={styles.smallLabel}>Presets</Text>
          <View style={styles.presetRow}>
            <PresetButton label="Clear" onPress={() => onApplyPreset?.('CLEAR')} />
            <PresetButton label="Select all" onPress={() => onApplyPreset?.('SELECT_ALL')} />
            <PresetButton label="Weekdays" onPress={() => onApplyPreset?.('WEEKDAYS')} />
            <PresetButton label="Weekend" onPress={() => onApplyPreset?.('WEEKEND')} />
            <PresetButton label="Mon -> weekdays" onPress={() => onApplyPreset?.('COPY_MONDAY_TO_WEEKDAYS')} />
          </View>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.gridScroll}>
            <View>
              <View style={styles.gridRow}>
                <View style={styles.cornerCell} />
                {HOURS.map((hour) => (
                  <TouchableOpacity key={hour} style={styles.hourHeader} onPress={() => onToggleHour?.(hour)}>
                    <Text style={styles.gridHeaderText}>{String(hour).padStart(2, '0')}</Text>
                  </TouchableOpacity>
                ))}
              </View>
              {DAYS.map((day, dayIndex) => (
                <View key={day} style={styles.gridRow}>
                  <View style={styles.dayColumn}>
                    <TouchableOpacity style={styles.dayButton} onPress={() => onToggleDay?.(dayIndex)}>
                      <Text style={styles.gridHeaderText}>{day}</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.dayAction} onPress={() => onCopyDay?.(dayIndex)}>
                      <Text style={styles.dayActionText}>Copy</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.dayAction} onPress={() => onPasteDay?.(dayIndex)}>
                      <Text style={styles.dayActionText}>Paste</Text>
                    </TouchableOpacity>
                  </View>
                  {HOURS.map((hour) => {
                    const selected = Boolean(state.weeklySlots[dayIndex * 24 + hour]);
                    return (
                      <TouchableOpacity
                        key={`${day}-${hour}`}
                        style={[styles.slot, selected ? styles.slotSelected : styles.slotUnselected]}
                        onPress={() => onToggleSlot?.(dayIndex, hour)}
                      />
                    );
                  })}
                </View>
              ))}
            </View>
          </ScrollView>
          <Text style={styles.summary}>{state.summary}</Text>
          <View style={styles.editorActions}>
            <TouchableOpacity style={styles.primaryButton} onPress={onSave}>
              <Text style={styles.primaryButtonText}>Save</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.inlineButton} onPress={onCancel}>
              <Text style={styles.inlineButtonText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </>
      ) : (
        <Text style={styles.line}>Editor closed</Text>
      )}
    </Card>
  );
}

export function LockCard({ state }) {
  return (
    <Card title="Lock">
      <Text style={styles.line}>{state.title}</Text>
      <Text style={styles.line}>Action: {state.action}</Text>
      {typeof state.remainingMs === 'number' ? <Text style={styles.line}>Remaining: {state.remainingMs} ms</Text> : null}
    </Card>
  );
}

export function DiagnosticsCard({ state }) {
  return (
    <Card title="Diagnostics">
      <Text style={styles.line}>VPN active: {state.vpnActive}</Text>
      <Text style={styles.line}>DNS interception: {state.dnsInterception}</Text>
      <Text style={styles.line}>Brave filtering test: {state.braveFilteringTest}</Text>
      <Text style={styles.line}>Currently blocked: {state.currentlyBlocked}</Text>
    </Card>
  );
}

export function OnboardingCard({ state }) {
  return (
    <Card title="Onboarding">
      <Text style={styles.summary}>{state.secureDnsWarning}</Text>
      <Text style={styles.line}>{state.braveStatus}</Text>
      <Text style={styles.line}>Upstream DNS: {state.upstreamDnsSummary}</Text>
      <Text style={styles.line}>{state.alwaysOnVpnSteps.join(' -> ')}</Text>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#F7F1E3',
    borderRadius: 20,
    padding: 16,
    gap: 8,
  },
  cardTitle: {
    color: '#13211D',
    fontSize: 20,
    fontWeight: '700',
  },
  line: {
    color: '#13211D',
    fontSize: 15,
  },
  summary: {
    color: '#355C4D',
    fontSize: 14,
    lineHeight: 20,
  },
  warning: {
    color: '#A3472C',
    fontWeight: '700',
  },
  rule: {
    borderTopColor: '#D6CCBA',
    borderTopWidth: 1,
    paddingTop: 8,
    gap: 4,
  },
  ruleDomain: {
    color: '#13211D',
    fontWeight: '700',
    fontSize: 16,
  },
  ruleActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    marginTop: 4,
  },
  smallLabel: {
    color: '#355C4D',
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  input: {
    borderWidth: 1,
    borderColor: '#D6CCBA',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: '#13211D',
    fontSize: 15,
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  inlineButton: {
    backgroundColor: '#13211D',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  inlineButtonText: {
    color: '#F7F1E3',
    fontWeight: '700',
  },
  primaryButton: {
    backgroundColor: '#D9A441',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    alignSelf: 'flex-start',
  },
  primaryButtonText: {
    color: '#13211D',
    fontWeight: '700',
  },
  modeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  modeButton: {
    borderWidth: 1,
    borderColor: '#355C4D',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  modeButtonActive: {
    backgroundColor: '#355C4D',
  },
  modeButtonText: {
    color: '#355C4D',
    fontWeight: '600',
  },
  modeButtonTextActive: {
    color: '#F7F1E3',
  },
  presetRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  presetButton: {
    backgroundColor: '#E8DFC9',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  presetButtonText: {
    color: '#13211D',
    fontWeight: '600',
  },
  gridScroll: {
    paddingBottom: 4,
  },
  gridRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  cornerCell: {
    width: 72,
  },
  hourHeader: {
    width: 34,
    alignItems: 'center',
    paddingVertical: 6,
  },
  gridHeaderText: {
    color: '#355C4D',
    fontSize: 12,
    fontWeight: '700',
  },
  dayColumn: {
    width: 72,
    paddingRight: 8,
    gap: 4,
  },
  dayButton: {
    backgroundColor: '#E8DFC9',
    borderRadius: 10,
    alignItems: 'center',
    paddingVertical: 6,
  },
  dayAction: {
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#D6CCBA',
    alignItems: 'center',
    paddingVertical: 4,
  },
  dayActionText: {
    color: '#355C4D',
    fontSize: 11,
    fontWeight: '700',
  },
  slot: {
    width: 28,
    height: 28,
    borderRadius: 6,
    margin: 3,
  },
  slotSelected: {
    backgroundColor: '#D9A441',
  },
  slotUnselected: {
    backgroundColor: '#E8DFC9',
    borderWidth: 1,
    borderColor: '#D6CCBA',
  },
  editorActions: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
});
