import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["src/**/*.test.ts"],
    environment: "node",
    // pglite + migrations can take a moment to spin up on first run.
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
});
