import { cp, mkdir, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Vite 산출물을 Spring Boot의 정적 리소스 위치에 복사한다.
const dist = new URL("../dist/", import.meta.url);
const staticRoot = new URL("../../src/main/resources/static/", import.meta.url);
// 해시가 바뀐 이전 빌드 파일이 계속 남지 않도록 정적 폴더를 먼저 비운다.
await rm(fileURLToPath(staticRoot), { recursive: true, force: true });
await mkdir(fileURLToPath(staticRoot), { recursive: true });
await cp(new URL("index.html", dist), new URL("index.html", staticRoot));
await cp(new URL("assets/", dist), new URL("assets/", staticRoot), {
  recursive: true,
  force: true,
});
console.log(`React build copied to ${fileURLToPath(staticRoot)}`);
