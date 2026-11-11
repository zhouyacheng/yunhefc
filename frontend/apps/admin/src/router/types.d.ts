import type { AdminSection } from '@yunhefc/permissions';

declare module 'vue-router' {
  interface RouteMeta {
    section?: AdminSection;
  }
}
