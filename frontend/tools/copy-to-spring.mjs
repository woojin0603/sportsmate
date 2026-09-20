import { cp, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Vite 산출물을 Spring Boot의 정적 리소스 위치에 복사한다.
const dist = new URL("../dist/", import.meta.url);
const staticRoot = new URL("../../src/main/resources/static/", import.meta.url);
await mkdir(fileURLToPath(staticRoot), { recursive: true });
await cp(new URL("index.html", dist), new URL("index.html", staticRoot));
await cp(new URL("assets/", dist), new URL("assets/", staticRoot), {
  recursive: true,
  force: true,
});
console.log(`React build copied to ${fileURLToPath(staticRoot)}`);
