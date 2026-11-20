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
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      '**/miniprogram_npm/**',
      'playwright-report/**',
      'test-results/**',
    ],
  },
  js.configs.recommended,
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  skipFormatting,
  {
    name: 'yunhefc/miniprogram-globals',
    files: ['apps/miniprogram/**/*.ts'],
    languageOptions: {
      globals: {
        wx: 'readonly',
        App: 'readonly',
        Page: 'readonly',
        Component: 'readonly',
        getApp: 'readonly',
        getCurrentPages: 'readonly',
      },
    },
  },
  {
    name: 'yunhefc/miniprogram-spike',
    files: ['apps/miniprogram/miniprogram/**/*.js'],
    languageOptions: {
      globals: {
        ...globals.node,
        wx: 'readonly',
        App: 'readonly',
        Page: 'readonly',
        Component: 'readonly',
      },
    },
    rules: {
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
  {
    name: 'yunhefc/node-globals',
    files: ['**/*.config.{js,ts}', 'e2e/**/*.ts', 'apps/miniprogram/scripts/**/*.mjs'],
    languageOptions: {
      globals: globals.node,
    },
  },
);
