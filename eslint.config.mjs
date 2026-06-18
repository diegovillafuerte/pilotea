import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Agent/task worktrees checked out inside the repo — not part of this checkout.
    ".claude/worktrees/**",
    ".worktrees/**",
    // Native Android project (Kotlin/Gradle) and its generated build artifacts —
    // not JS source, must not be linted by the web eslint.
    "android/**",
    // claude.ai/design bundle synced by the design-sync skill — generated reference
    // templates/ui-kits, not imported by the app. It ships its own oxlint adherence
    // config (design-system/_adherence.oxlintrc.json) and must not be linted by the web eslint.
    "design-system/**",
  ]),
]);

export default eslintConfig;
