const DOMAIN_PATTERN =
  /^(?=.{1,253}$)(?:(?!-)[a-z0-9-]{1,63}(?<!-)\.)+(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)$/;

export function normalizeDomain(input) {
  const trimmed = input.trim().toLowerCase();
  const withoutDot = trimmed.endsWith('.') ? trimmed.slice(0, -1) : trimmed;

  if (withoutDot.length === 0) {
    throw new Error('INVALID_DOMAIN');
  }

  if (withoutDot.includes('://') || withoutDot.includes('/') || withoutDot.includes(':')) {
    throw new Error('INVALID_DOMAIN');
  }

  const ascii = new URL(`http://${withoutDot}`).hostname.toLowerCase();

  if (ascii !== withoutDot) {
    throw new Error('INVALID_DOMAIN');
  }

  if (!DOMAIN_PATTERN.test(ascii)) {
    throw new Error('INVALID_DOMAIN');
  }

  return ascii;
}

export function assertUniqueDomain(domain, existingDomains) {
  if (existingDomains.includes(domain)) {
    throw new Error('DUPLICATE_DOMAIN');
  }
}
