import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  exportShareCover,
  fitText,
  generateShareCover,
  textOr,
  wrapText,
} from '../miniprogram/utils/share-cover';
import { stubShareCanvas } from './support/share-cover-fakes';

const measure = (text: string): number => text.length * 10;

describe('fitText', () => {
  it('keeps text that fits and truncates with an ellipsis otherwise', () => {
    expect(fitText(measure, 'ABCD', 40)).toBe('ABCD');
    expect(fitText(measure, 'ABCDE', 40)).toBe('ABC…');
    expect(fitText(measure, '报名满员联调赛', 60)).toBe('报名满员联…');
  });
});

describe('wrapText', () => {
  it('wraps by measured width and caps the line count', () => {
    expect(wrapText(measure, 'ABCDEFGH', 40, 2)).toEqual(['ABCD', 'EFGH']);
    expect(wrapText(measure, 'ABCDEFGHIJ', 40, 2)).toEqual(['ABCD', 'EFG…']);
  });

  it('returns a single line when it fits', () => {
    expect(wrapText(measure, '报名满员', 80, 2)).toEqual(['报名满员']);
  });

  it('returns no lines when the line budget is zero', () => {
    expect(wrapText(measure, '报名满员', 80, 0)).toEqual([]);
  });
});

describe('textOr', () => {
  it('keeps non-empty text and replaces empty values with a dash', () => {
    expect(textOr('聚龙足球场')).toBe('聚龙足球场');
    expect(textOr('')).toBe('—');
  });
});

describe('generateShareCover', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns the rendered cover path', async () => {
    stubShareCanvas();
    await expect(
      generateShareCover('#cover', async () => '/tmp/generated.png', '/assets/fallback.png'),
    ).resolves.toBe('/tmp/generated.png');
  });

  it('falls back when the canvas node is missing', async () => {
    stubShareCanvas({ node: false });
    const render = vi.fn(async () => '/tmp/generated.png');
    await expect(generateShareCover('#cover', render, '/assets/fallback.png')).resolves.toBe(
      '/assets/fallback.png',
    );
    expect(render).not.toHaveBeenCalled();
  });

  it('falls back when rendering throws', async () => {
    stubShareCanvas();
    await expect(
      generateShareCover(
        '#cover',
        async () => {
          throw new Error('draw failed');
        },
        '/assets/fallback.png',
      ),
    ).resolves.toBe('/assets/fallback.png');
  });

  it('falls back when the canvas export fails', async () => {
    stubShareCanvas({ exportFailure: 'canvasToTempFilePath:fail' });
    await expect(
      generateShareCover('#cover', (canvas) => exportShareCover(canvas), '/assets/fallback.png'),
    ).resolves.toBe('/assets/fallback.png');
  });
});
