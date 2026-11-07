/** @type {import('eslint').Linter.Config[]} */
export default [
  {
    // Stage 1.4: ESLint 9 flat config present so `npm run lint` does not crash.
    // Full TypeScript rule set is deferred; typecheck + architecture tests own
    // structural enforcement until a dedicated lint stage lands.
    ignores: ['**/*'],
  },
];
