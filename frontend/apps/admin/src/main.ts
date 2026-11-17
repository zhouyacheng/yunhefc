import { installChunkReloadGuard } from '@yunhefc/shared';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import 'element-plus/dist/index.css';

import App from './App.vue';
import { router } from './router';
import './styles/tokens.css';
import './styles/element-overrides.css';
import './styles/main.css';

const app = createApp(App);

installChunkReloadGuard(router);
app.use(createPinia());
app.use(ElementPlus, { locale: zhCn });
app.use(router);
app.mount('#app');
