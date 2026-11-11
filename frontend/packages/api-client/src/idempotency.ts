/** 05 §2.6 高风险写接口的 Idempotency-Key；首个切片只预留。 */
export function newIdempotencyKey(): string {
  return globalThis.crypto.randomUUID();
}
