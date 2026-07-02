<template>
  <el-container class="app-root">
    <el-aside width="220px" class="app-aside">
      <div class="logo">转发平台</div>
      <el-menu :default-active="activeMenu" router class="app-menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon><span>监控大盘</span>
        </el-menu-item>
        <el-menu-item index="/protocols">
          <el-icon><Connection /></el-icon><span>转发协议</span>
        </el-menu-item>
        <el-menu-item index="/data-sources">
          <el-icon><Download /></el-icon><span>数据源</span>
        </el-menu-item>
        <el-menu-item index="/output-targets">
          <el-icon><Upload /></el-icon><span>输出目标</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon><span>日志查询</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span>MQTT / Kafka 数据转发处理平台</span>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
.app-root { height: 100vh; }
.app-aside { background: #001529; }
.logo { color: #fff; font-size: 18px; font-weight: 600; text-align: center; padding: 18px 0; letter-spacing: 2px; }
.app-menu { background: #001529; border-right: none; }
.app-menu .el-menu-item { color: #c0c4cc; }
.app-menu .el-menu-item.is-active { color: #fff; background: #1890ff; }
.app-header { display: flex; align-items: center; background: #fff; border-bottom: 1px solid #eee; font-weight: 600; }
.app-main { background: #f0f2f5; padding: 16px; }
</style>
