import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/protocols', name: 'protocols', component: () => import('../views/ProtocolsView.vue') },
  { path: '/protocols/:id/editor', name: 'editor', component: () => import('../views/ProtocolEditorView.vue') },
  { path: '/data-sources', name: 'dataSources', component: () => import('../views/DataSourcesView.vue') },
  { path: '/output-targets', name: 'outputTargets', component: () => import('../views/OutputTargetsView.vue') },
  { path: '/logs', name: 'logs', component: () => import('../views/LogsView.vue') }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
