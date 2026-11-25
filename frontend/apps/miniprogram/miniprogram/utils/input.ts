/** 原生 input 的 bindinput 与 picker 的 bindchange 都把值放在 detail.value 上。 */
export interface ValueEvent {
  detail: { value: string };
}

/** 中国大陆手机号。 */
export const PHONE_PATTERN = /^1\d{10}$/;
