/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 无后台权限时的 C 端地址；生产构建按部署域名配置。 */
  readonly VITE_WEB_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
