/** @type {import('eslint').Linter.Config[]} */
export default [
  {
    ignores: ['node_modules/**', 'dist/**', '.wrangler/**', 'coverage/**'],
  },
  {
    // Stage 1.4/1.5: keep lint fail-closed without a full TypeScript rule set.
    // Files are matched so ESLint does not exit 2 for "all files ignored".
    // Structural enforcement remains `tsc --noEmit` + unit tests.
    files: ['src/**/*.ts', 'scripts/**/*.ts', 'tests/**/*.ts'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      // Treat TypeScript as plain modules; parser accepts most TS with this
      // combination when no type-aware rules are enabled.
      parserOptions: {
        ecmaFeatures: { modules: true },
      },
    },
    rules: {},
  },
];
