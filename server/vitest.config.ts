import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// 단위 테스트는 Node에서 돈다. Workers 전용 모듈은 가짜로 바꿔 끼운다.
export default defineConfig({
  resolve: {
    alias: {
      "cloudflare:workers": fileURLToPath(new URL("./test/stubs/cloudflare-workers.ts", import.meta.url)),
    },
  },
  test: { include: ["test/**/*.test.ts"] },
});
