<template>
  <el-container class="h-screen w-screen bg-tech-dark text-gray-900 overflow-hidden font-sans">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '240px'" class="bg-white border-r border-gray-100 transition-all duration-500 ease-spring relative z-20 shadow-sm">
      <div class="h-16 flex items-center justify-center cursor-pointer hover:opacity-80 transition-opacity" @click="router.push('/')">
        <div class="w-8 h-8 rounded-xl bg-black flex items-center justify-center shadow-sm">
          <el-icon color="white"><DataAnalysis /></el-icon>
        </div>
        <span v-if="!isCollapse" class="ml-3 font-semibold text-lg tracking-tight whitespace-nowrap text-gray-900">SaaS Pro</span>
      </div>

      <el-menu
        :default-active="route.path"
        class="border-none mt-6 bg-transparent"
        :collapse="isCollapse"
        router
        text-color="#86868b"
        active-text-color="#1d1d1f"
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <template #title><span class="font-medium">数据大盘</span></template>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><ShoppingBag /></el-icon>
          <template #title><span class="font-medium">商品管理</span></template>
        </el-menu-item>
        <el-menu-item index="/inventories">
          <el-icon><Goods /></el-icon>
          <template #title><span class="font-medium">库存管理</span></template>
        </el-menu-item>
        <el-menu-item index="/sales">
          <el-icon><ShoppingCart /></el-icon>
          <template #title><span class="font-medium">销售数据</span></template>
        </el-menu-item>
        <el-menu-item index="/jobs">
          <el-icon><List /></el-icon>
          <template #title><span class="font-medium">导入任务</span></template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主体区域 -->
    <el-container class="flex flex-col relative bg-[#f5f5f7]">
      <!-- 顶部导航 -->
      <el-header class="h-16 bg-white/70 backdrop-blur-[20px] flex items-center justify-between px-8 z-10 sticky top-0 border-b border-gray-200/50">
        <div class="flex items-center">
          <el-icon 
            class="cursor-pointer text-gray-500 hover:text-gray-900 transition-colors duration-300" 
            :size="20" 
            @click="isCollapse = !isCollapse"
          >
            <Fold v-if="!isCollapse"/>
            <Expand v-else/>
          </el-icon>
        </div>

        <div class="flex items-center space-x-6">

          
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="flex items-center cursor-pointer outline-none group">
              <el-avatar :size="32" class="bg-gray-200 text-gray-600 font-medium group-hover:bg-gray-300 transition-colors duration-300">
                {{ authStore.username?.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="ml-2 text-sm font-medium text-gray-700 group-hover:text-gray-900 transition-colors duration-300">{{ authStore.username }}</span>
              <el-icon class="ml-1 text-gray-400 group-hover:text-gray-600"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="glass-card !p-1 !rounded-xl">
                <el-dropdown-item command="logout" class="text-red-500 !rounded-lg font-medium">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 页面内容 -->
      <el-main class="p-8 overflow-y-auto relative">
        <router-view v-slot="{ Component }">
          <transition name="slide-in" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { ElMessage } from 'element-plus'
import { 
  DataAnalysis, Odometer, ShoppingCart, ShoppingBag, List, Goods,
  Fold, Expand, ArrowDown 
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isCollapse = ref(false)

const handleCommand = (command: string) => {
  if (command === 'logout') {
    authStore.logout()
    ElMessage.success('已就绪。数据现已归位。')
    router.push('/login')
  }
}
</script>

<style scoped>
:deep(.el-menu-item) {
  margin: 4px 16px;
  border-radius: 12px;
  height: 44px;
  line-height: 44px;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}
:deep(.el-menu-item.is-active) {
  background-color: #f2f2f7 !important;
  color: #1d1d1f !important;
  font-weight: 600;
}
:deep(.el-menu-item:hover) {
  background-color: #f5f5f7;
}
:deep(.el-aside) {
  border-right: 1px solid rgba(0,0,0,0.05);
}
</style>