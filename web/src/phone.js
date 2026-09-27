// 저장 형식과 관계없이 일반 전화번호와 대표번호를 하이픈으로 읽기 쉽게 표시한다.
export function formatPhone(value) {
  const digits = String(value || "").replace(/\D/g, "");
  if (digits.length === 8) return `${digits.slice(0, 4)}-${digits.slice(4)}`;
  const prefixLength = digits.startsWith("02") ? 2 : 3;
  const local = digits.slice(prefixLength);
  if (local.length !== 7 && local.length !== 8) return value;
  return `${digits.slice(0, prefixLength)}-${local.slice(0, -4)}-${local.slice(-4)}`;
}
