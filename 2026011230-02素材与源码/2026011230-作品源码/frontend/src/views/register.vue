<template>
  <div class="h-screen w-screen bg-[#f5f5f7] flex items-center justify-center relative overflow-hidden">
    <!-- 背景动画效果 -->
    <div class="absolute inset-0 z-0">
      <div class="absolute top-[-20%] left-[-10%] w-[70vw] h-[70vw] rounded-full bg-apple-blue/5 blur-[120px] animate-spin-slow"></div>
      <div class="absolute bottom-[-20%] right-[-10%] w-[60vw] h-[60vw] rounded-full bg-blue-400/5 blur-[100px] animate-spin-slow" style="animation-direction: reverse;"></div>
    </div>

    <!-- 注册卡片 -->
    <div class="z-10 w-full max-w-md p-10 glass-card !bg-white/80 rounded-3xl animate-float shadow-[0_20px_40px_rgba(0,0,0,0.04)]" style="animation-duration: 8s;">
      <div class="text-center mb-10">
        <div class="w-16 h-16 bg-black rounded-[20px] flex items-center justify-center mx-auto mb-6 shadow-md transition-transform duration-500 hover:scale-105">
          <el-icon :size="32" color="white"><DataAnalysis /></el-icon>
        </div>
        <h1 class="text-3xl font-bold text-gray-900 mb-2 tracking-tight">SaaS Pro</h1>
        <p class="text-gray-500 text-sm font-medium">开启您的专属空间</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleRegister" size="large" class="space-y-5">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="设置账户名" class="!h-12 text-base">
            <template #prefix>
              <el-icon class="text-gray-400"><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="设置密码" show-password class="!h-12 text-base">
            <template #prefix>
              <el-icon class="text-gray-400"><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item prop="role">
          <el-select v-model="form.role" placeholder="选择权限" class="w-full !h-12 text-base">
            <el-option label="标准权限" value="USER" />
            <el-option label="管理权限" value="ADMIN" />
          </el-select>
        </el-form-item>

        <el-form-item class="pt-4">
          <el-button 
            class="w-full !bg-black !text-white border-0 hover:!bg-gray-800 transition-all duration-300 ease-spring shadow-md hover:shadow-lg h-12 text-base font-semibold rounded-2xl active:scale-[0.98]"
            :loading="loading" 
            @click="handleRegister"
          >
            {{ loading ? '配置中...' : '完成创建' }}
          </el-button>
        </el-form-item>
        
        <div class="text-center mt-6">
          <el-link type="info" :underline="false" class="text-sm font-medium hover:text-black transition-colors" @click="router.push('/login')">
            <el-icon class="mr-1"><ArrowLeft /></el-icon> 返回登录
          </el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, User, Lock, ArrowLeft } from '@element-plus/icons-vue'
import api from '../utils/api'

const router = useRouter()
const formRef = ref()

const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  role: 'USER'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const handleRegister = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        const res: any = await api.post('/auth/register', {
          username: form.username,
          password: form.password,
          role: form.role
        })
        if (res.code === 200) {
          ElMessage.success({ message: '注册成功，请登录', grouping: true })
          router.push('/login')
        } else {
          ElMessage.error(res.message || '注册失败')
        }
      } catch (e: any) {
        console.error('Register failed:', e)
        ElMessage.error(e.response?.data?.message || '网络连接失败，请检查后端服务是否启动')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>