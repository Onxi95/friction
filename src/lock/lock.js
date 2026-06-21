export const UNLOCK_DELAY_MS = 5 * 60 * 1000;

export function createLockedState() {
  return { type: 'LOCKED' };
}

export function createUnlockedState() {
  return { type: 'UNLOCKED' };
}

export function startUnlockCountdown(startedElapsedMs, bootCount) {
  return {
    type: 'UNLOCK_PENDING',
    startedElapsedMs,
    bootCount,
  };
}

export function getUnlockStatus(lockState, elapsedRealtimeMs, bootCount) {
  if (lockState.type !== 'UNLOCK_PENDING') {
    return lockState.type === 'UNLOCKED'
      ? { state: 'UNLOCKED' }
      : { state: 'LOCKED' };
  }

  if (lockState.bootCount !== bootCount) {
    return { state: 'LOCKED' };
  }

  const elapsed = Math.max(0, elapsedRealtimeMs - lockState.startedElapsedMs);
  const remainingMs = Math.max(0, UNLOCK_DELAY_MS - elapsed);

  return {
    state: 'UNLOCK_PENDING',
    remainingMs,
    canConfirm: remainingMs === 0,
  };
}

export function confirmUnlock(lockState, elapsedRealtimeMs, bootCount) {
  const status = getUnlockStatus(lockState, elapsedRealtimeMs, bootCount);

  if (status.state !== 'UNLOCK_PENDING' || !status.canConfirm) {
    throw new Error('COUNTDOWN_STILL_ACTIVE');
  }

  return createUnlockedState();
}

export function relockAfterWrite() {
  return createLockedState();
}

export function requireEditingUnlocked(lockState) {
  if (lockState.type !== 'UNLOCKED') {
    throw new Error('EDITING_LOCKED');
  }
}
