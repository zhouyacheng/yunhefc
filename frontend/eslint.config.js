import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript';
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting';
import globals from 'globals';

export default defineConfigWithVueTs(
  {
    name: 'yunhefc/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'yunhefc/files-to-ignore',
    ignores: ['**/dist/**', '**/node_modules/**', 'playwright-report/**', 'test-results/**'],
  },
  js.configs.recommended,
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  skipFormatting,
  {
    name: 'yunhefc/node-globals',
    files: ['**/*.config.{js,ts}', 'e2e/**/*.ts'],
    languageOptions: {
      globals: globals.node,
    },
  },
);
