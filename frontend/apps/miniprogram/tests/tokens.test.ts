import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

function tokensOf(filePath: string): Record<string, string> {
  const content = readFileSync(filePath, 'utf8');
  const tokens: Record<string, string> = {};
  const pattern = /--yh-[a-z0-9-]+:\s*[^;]+;/g;
  for (const match of content.match(pattern) || []) {
    const [name, value] = match.split(':');
    const key = name.trim();
    // 只取基础值；web 里的媒体查询覆盖（如窄屏 page-pad）不属于小程序端基线。
    if (!(key in tokens)) {
      tokens[key] = value.replace(';', '').trim();
    }
  }
  return tokens;
}

describe('design tokens', () => {
  it('keeps miniprogram tokens identical to web', () => {
    const web = fileURLToPath(new URL('../../../apps/web/src/styles/main.css', import.meta.url));
    const mini = fileURLToPath(new URL('../miniprogram/styles/tokens.wxss', import.meta.url));
    expect(tokensOf(mini)).toEqual(tokensOf(web));
  });
});
