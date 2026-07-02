import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'
import { loader } from '@guolao/vue-monaco-editor'

import App from './App.vue'
import router from './router'

// 配置 monaco 的 web worker（Vite 原生 worker 导入）
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'

// @ts-ignore
self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker()
  }
}
loader.config({ monaco })

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.mount('#app')
