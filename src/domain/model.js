export const MatchMode = Object.freeze({
  EXACT: 'EXACT',
  DOMAIN_AND_SUBDOMAINS: 'DOMAIN_AND_SUBDOMAINS',
});

export const ScheduleMode = Object.freeze({
  BLOCK_DURING_SELECTED_HOURS: 'BLOCK_DURING_SELECTED_HOURS',
  ALLOW_ONLY_DURING_SELECTED_HOURS: 'ALLOW_ONLY_DURING_SELECTED_HOURS',
});

export function createWeeklySlots(defaultValue = false) {
  return Array.from({ length: 168 }, () => defaultValue);
}

export function createDomainRule({
  id,
  domain,
  enabled = true,
  matchMode = MatchMode.DOMAIN_AND_SUBDOMAINS,
  scheduleMode = ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS,
  weeklySlots = createWeeklySlots(false),
}) {
  if (weeklySlots.length !== 168) {
    throw new Error('INVALID_SCHEDULE');
  }

  return {
    id,
    domain,
    enabled,
    matchMode,
    scheduleMode,
    weeklySlots: [...weeklySlots],
  };
}
