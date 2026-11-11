import { describe, expect, it } from 'vitest';

import { emptyPage, isPage, matchStatusText, matchTypeText, readLabel } from './index';

describe('Page helper', () => {
  it('recognizes the 05 pagination shape', () => {
    expect(isPage({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 })).toBe(true);
    expect(isPage({ items: [] })).toBe(false);
    expect(isPage(null)).toBe(false);
  });

  it('builds an empty page', () => {
    expect(emptyPage(20)).toEqual({ items: [], total: 0, page: 1, pageSize: 20, totalPages: 0 });
  });
});

describe('enum helpers', () => {
  it('reads the sibling label field', () => {
    expect(readLabel({ status: 4, statusLabel: 'FINISHED' }, 'status')).toBe('FINISHED');
    expect(readLabel({ status: 4 }, 'status')).toBeUndefined();
  });

  it('maps 04 §2.4 match codes to display text', () => {
    expect(matchStatusText(2)).toBe('已锁定');
    expect(matchTypeText(1)).toBe('队内赛');
    expect(matchStatusText(undefined)).toBe('');
  });
});
