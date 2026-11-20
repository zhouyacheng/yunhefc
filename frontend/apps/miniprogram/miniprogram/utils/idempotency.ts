// 微信基础库不保证 crypto.randomUUID；用时间戳 + 进程内计数 + 随机串生成，
// 长度远小于后端 64 字符上限。
let counter = 0;

export function newIdempotencyKey(
  now: number = Date.now(),
  random: () => number = Math.random,
): string {
  counter = (counter + 1) % 1_679_616; // base36 四位
  const time = now.toString(36);
  const seq = counter.toString(36).padStart(4, '0');
  const noise = Math.floor(random() * 0x100000000)
    .toString(36)
    .padStart(7, '0');
  return `mp-${time}-${seq}-${noise}`;
}
