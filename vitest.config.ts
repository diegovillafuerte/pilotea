import { configDefaults, defineConfig } from "vitest/config";
import path from "path";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    // Agent/task worktrees checked out inside the repo — not part of this checkout.
    exclude: [...configDefaults.exclude, "**/.claude/worktrees/**", "**/.worktrees/**"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
