import { createReadStream, writeFileSync } from "node:fs";
import { readdir } from "node:fs/promises";
import { StringDecoder } from "node:string_decoder";
import path from "node:path";

// 공개 원본의 개인 식별자와 측정값은 버리고, 처방 빈도와 센터 연락처만 추출한다.
const sourceDir = process.argv[2];
if (!sourceDir) {
  throw new Error("Usage: node tools/build_fitness_prescriptions.mjs <JSON directory>");
}
const names = await readdir(sourceDir);
const sourceFiles = [
  ...names.filter((name) => /^KS_DSPSN_FTNESS_MESURE_ACCTO_MVM_PRSCRPTN_LIST_2025\d{2}\.json$/.test(name)).sort(),
  ...names.filter((name) => /^KS_NFA_FTNESS_MESURE_MVN_PRSCRPTN_GNRLZ_INFO_2026\d{2}\.json$/.test(name)).sort(),
  "KS_LC_IFRA_FTNESS_MESURE_MVM_PRSCRPTN_INFO_202607_dnl.json",
];
const counts = new Map();
const centers = new Map();
const stats = [];

// 각 파일을 스트리밍하며 JSON 배열의 객체를 하나씩 파싱한다.
async function forEachObject(file, consume) {
  const decoder = new StringDecoder("utf8");
  let buffer = "";
  let depth = 0;
  let quoted = false;
  let escaped = false;
  let rows = 0;
  for await (const chunk of createReadStream(file)) {
    const text = decoder.write(chunk);
    let start = 0;
    for (let i = 0; i < text.length; i++) {
      const char = text[i];
      if (depth === 0) {
        if (char === "{") {
          depth = 1;
          start = i;
        }
        continue;
      }
      if (quoted) {
        if (escaped) escaped = false;
        else if (char === "\\") escaped = true;
        else if (char === '"') quoted = false;
      } else if (char === '"') quoted = true;
      else if (char === "{") depth++;
      else if (char === "}" && --depth === 0) {
        buffer += text.slice(start, i + 1);
        consume(JSON.parse(buffer));
        rows++;
        buffer = "";
        start = i + 1;
      }
    }
    if (depth > 0) buffer += text.slice(start);
  }
  if (depth !== 0) throw new Error(`Incomplete JSON object in ${file}`);
  return rows;
}

// 처방 구분자 안의 '/'가 운동명에 쓰일 수 있어 단계 머리말만 분리한다.
function splitRoutine(value) {
  const normalized = value.replace(/\s*\/\s*(?=(?:사전|준비|본|마무리|정리)운동\s*:)/g, " | ");
  const parts = { warmup: [], main: [], cooldown: [] };
  for (const segment of normalized.split(/\s*\|\s*/)) {
    const match = segment.match(/^(사전운동|준비운동|본운동|마무리운동|정리운동)\s*:\s*(.*)$/);
    if (!match) continue;
    const target = /사전|준비/.test(match[1]) ? "warmup" : /본/.test(match[1]) ? "main" : "cooldown";
    parts[target].push(...match[2].split(/\s*,\s*/).filter(Boolean));
  }
  return parts;
}

// 개인 행을 연령대·성별·등급·장애 유형별 처방 빈도로 집계한다.
function collect(row, source) {
  const routine = row.MVM_PRSCRPTN_CN?.trim();
  const age = Number(row.MESURE_AGE_CO);
  if (routine && Number.isInteger(age) && age >= 10 && age <= 120) {
    const ageBand = age >= 70 ? "70대 이상" : `${Math.floor(age / 10) * 10}대`;
    const sex = row.SEXDSTN_FLAG_CD === "M" ? "M" : row.SEXDSTN_FLAG_CD === "F" ? "F" : "UNKNOWN";
    const grade = source === "DISABILITY" ? "" : row.CRTFC_FLAG_NM || "미분류";
    const disabilityType = source === "DISABILITY" ? row.TROBL_TY_NM || "미분류" : "";
    const key = JSON.stringify([source, ageBand, sex, grade, disabilityType, routine]);
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  if (source === "LOCAL") {
    const centerName = row.CNTER_NM?.trim();
    const province = row.CTPRVN_NM?.trim();
    if (centerName && province) {
      const center = {
        name: centerName,
        province,
        district: row.GUGUN_NM || "",
        road: row.ROAD_NM || "",
        buildingNumber: row.BULD_NO || "",
        detailAddress: row.DETAIL_ADDR || "",
        phone: row.REPRSNT_TEL_NO || "",
        operatingDays: row.OPER_PD || "",
        operatingHours: row.OPER_TIME || "",
        latitude: Number(row.CNTER_LA) || null,
        longitude: Number(row.CNTER_LO) || null,
      };
      centers.set(`${province}|${centerName}|${center.district}`, center);
    }
  }
}

for (const name of sourceFiles) {
  if (!names.includes(name)) throw new Error(`Missing source file: ${name}`);
  const source = name.includes("DSPSN") ? "DISABILITY" : name.includes("LC_IFRA") ? "LOCAL" : "GENERAL";
  const rows = await forEachObject(path.join(sourceDir, name), (row) => collect(row, source));
  stats.push({ file: name, source, rows });
  console.log(`${name}: ${rows} rows`);
}

// 각 조건에서 가장 빈번한 처방 5개만 보존한다.
const cohorts = new Map();
for (const [key, count] of counts) {
  const [source, ageBand, sex, grade, disabilityType, text] = JSON.parse(key);
  const cohortKey = JSON.stringify([source, ageBand, sex, grade, disabilityType]);
  const list = cohorts.get(cohortKey) || [];
  list.push({ source, ageBand, sex, grade, disabilityType, count, ...splitRoutine(text) });
  cohorts.set(cohortKey, list);
}
const prescriptions = [...cohorts.values()].flatMap((list) => list.filter((item) => item.count >= 5).sort((a, b) => b.count - a.count).slice(0, 5));
const output = { stats, prescriptions, centers: [...centers.values()] };
const destination = new URL("../src/main/resources/fitness-prescription-catalog.json", import.meta.url);
writeFileSync(destination, JSON.stringify(output));
console.log(`Prescriptions: ${prescriptions.length}; centers: ${centers.size}`);
