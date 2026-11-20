// 真机调试改为 'prod'，并确保域名已加入小程序后台 request 合法域名。
const ENV: 'local' | 'prod' = 'prod';

const BASE_URLS = {
  local: 'http://localhost:8080/api/v1/c',
  prod: 'https://www.yunhefc.com/api/v1/c',
} as const;

const WEB_URLS = {
  local: 'http://localhost:5273',
  prod: 'https://www.yunhefc.com',
} as const;

export const BASE_URL: string = BASE_URLS[ENV];
export const WEB_URL: string = WEB_URLS[ENV];
