// 把 @vant/weapp 从 pnpm 的符号链接目录平铺拷贝到 miniprogram_npm，
// 因为微信开发者工具的「构建 npm」不认 pnpm 的 symlink 布局。
import { createRequire } from 'node:module';
import { cpSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const packageJsonPath = require.resolve('@vant/weapp/package.json');
const packageJson = require(packageJsonPath);
const source = join(dirname(packageJsonPath), packageJson.miniprogram || 'lib');

const scriptDir = dirname(fileURLToPath(import.meta.url));
const dest = join(scriptDir, '..', 'miniprogram', 'miniprogram_npm', '@vant', 'weapp');

rmSync(dest, { recursive: true, force: true });
mkdirSync(dirname(dest), { recursive: true });
cpSync(source, dest, { recursive: true, dereference: true });

console.log(`@vant/weapp -> ${dest}`);
