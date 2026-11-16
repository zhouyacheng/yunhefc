/** C 端地址：生产按部署配置，开发默认本机 5273。 */
export const webUrl =
  import.meta.env.VITE_WEB_URL ?? (import.meta.env.DEV ? 'http://localhost:5273' : '');
