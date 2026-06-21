import { ScheduleMode } from './model.js';

export function getSlotIndex(dayIndex, hour) {
  if (dayIndex < 0 || dayIndex > 6 || hour < 0 || hour > 23) {
    throw new Error('INVALID_SCHEDULE');
  }

  return dayIndex * 24 + hour;
}

export function shouldBlock(rule, now) {
  if (!rule.enabled) {
    return false;
  }

  const dayIndex = (now.getDay() + 6) % 7;
  const slotIndex = getSlotIndex(dayIndex, now.getHours());
  const selected = Boolean(rule.weeklySlots[slotIndex]);

  return rule.scheduleMode === ScheduleMode.BLOCK_DURING_SELECTED_HOURS
    ? selected
    : !selected;
}

export function toggleSlot(weeklySlots, dayIndex, hour) {
  const slotIndex = getSlotIndex(dayIndex, hour);
  const next = [...weeklySlots];
  next[slotIndex] = !next[slotIndex];
  return next;
}

export function setSlot(weeklySlots, dayIndex, hour, selected) {
  const slotIndex = getSlotIndex(dayIndex, hour);
  const next = [...weeklySlots];
  next[slotIndex] = selected;
  return next;
}

export function applyDragSelection(
  weeklySlots,
  startDayIndex,
  startHour,
  endDayIndex,
  endHour,
  selected,
) {
  const startIndex = getSlotIndex(startDayIndex, startHour);
  const endIndex = getSlotIndex(endDayIndex, endHour);
  const from = Math.min(startIndex, endIndex);
  const to = Math.max(startIndex, endIndex);
  const next = [...weeklySlots];

  for (let index = from; index <= to; index += 1) {
    next[index] = selected;
  }

  return next;
}

export function toggleDay(weeklySlots, dayIndex) {
  if (dayIndex < 0 || dayIndex > 6) {
    throw new Error('INVALID_SCHEDULE');
  }

  const startIndex = getSlotIndex(dayIndex, 0);
  const endIndex = getSlotIndex(dayIndex, 23);
  const currentDaySlots = weeklySlots.slice(startIndex, endIndex + 1);
  const nextValue = currentDaySlots.some((slot) => !slot);
  const next = [...weeklySlots];

  for (let index = startIndex; index <= endIndex; index += 1) {
    next[index] = nextValue;
  }

  return next;
}

export function toggleHour(weeklySlots, hour) {
  if (hour < 0 || hour > 23) {
    throw new Error('INVALID_SCHEDULE');
  }

  const indexes = Array.from({ length: 7 }, (_, dayIndex) => getSlotIndex(dayIndex, hour));
  const nextValue = indexes.some((index) => !weeklySlots[index]);
  const next = [...weeklySlots];

  indexes.forEach((index) => {
    next[index] = nextValue;
  });

  return next;
}

export function copyDay(weeklySlots, dayIndex) {
  if (dayIndex < 0 || dayIndex > 6) {
    throw new Error('INVALID_SCHEDULE');
  }

  const startIndex = getSlotIndex(dayIndex, 0);
  return weeklySlots.slice(startIndex, startIndex + 24);
}

export function pasteDay(weeklySlots, dayIndex, copiedDaySlots) {
  if (dayIndex < 0 || dayIndex > 6 || copiedDaySlots.length !== 24) {
    throw new Error('INVALID_SCHEDULE');
  }

  const startIndex = getSlotIndex(dayIndex, 0);
  const next = [...weeklySlots];

  copiedDaySlots.forEach((value, offset) => {
    next[startIndex + offset] = value;
  });

  return next;
}

export function applyPreset(weeklySlots, preset) {
  const next = [...weeklySlots];

  if (preset === 'CLEAR') {
    return next.map(() => false);
  }

  if (preset === 'SELECT_ALL') {
    return next.map(() => true);
  }

  if (preset === 'WEEKDAYS') {
    return next.map((_, index) => Math.floor(index / 24) <= 4);
  }

  if (preset === 'WEEKEND') {
    return next.map((_, index) => Math.floor(index / 24) >= 5);
  }

  if (preset === 'COPY_MONDAY_TO_WEEKDAYS') {
    const monday = copyDay(next, 0);
    return [1, 2, 3, 4].reduce(
      (current, dayIndex) => pasteDay(current, dayIndex, monday),
      next,
    );
  }

  throw new Error('UNKNOWN_PRESET');
}

function formatDay(dayIndex) {
  return ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][dayIndex];
}

function formatHour(hour) {
  return `${String(hour).padStart(2, '0')}:00`;
}

function collectRanges(daySlots) {
  const ranges = [];
  let start = -1;

  for (let hour = 0; hour <= 24; hour += 1) {
    const selected = hour < 24 ? daySlots[hour] : false;

    if (selected && start === -1) {
      start = hour;
    }

    if (!selected && start !== -1) {
      ranges.push([start, hour]);
      start = -1;
    }
  }

  return ranges;
}

export function summarizeSchedule(domain, weeklySlots, scheduleMode) {
  const parts = [];

  for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
    const daySlots = weeklySlots.slice(dayIndex * 24, dayIndex * 24 + 24);
    const ranges = collectRanges(daySlots);

    if (ranges.length === 0) {
      continue;
    }

    const formatted = ranges
      .map(([start, end]) => `${formatHour(start)}-${formatHour(end)}`)
      .join(', ');

    parts.push(`${formatDay(dayIndex)} ${formatted}`);
  }

  if (parts.length === 0) {
    return scheduleMode === ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS
      ? `${domain} is blocked all week.`
      : `${domain} is allowed all week.`;
  }

  const verb =
    scheduleMode === ScheduleMode.ALLOW_ONLY_DURING_SELECTED_HOURS ? 'available' : 'blocked';

  return `${domain} is ${verb} during ${parts.join('; ')}.`;
}
