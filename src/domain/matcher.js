import { MatchMode } from './model.js';

export function matchesExact(query, rule) {
  return query === rule;
}

export function matchesDomainAndSubdomains(query, rule) {
  return query === rule || query.endsWith(`.${rule}`);
}

export function matchesRule(query, rule) {
  if (rule.matchMode === MatchMode.EXACT) {
    return matchesExact(query, rule.domain);
  }

  return matchesDomainAndSubdomains(query, rule.domain);
}
