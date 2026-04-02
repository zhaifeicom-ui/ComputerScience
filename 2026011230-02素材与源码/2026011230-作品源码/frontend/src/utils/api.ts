import axios from 'axios'
import { useAuthStore } from '../store/auth'
import { ElMessage } from 'element-plus'
import router from '../router'
const baseURL = import.meta.env.VITE_APP_API_URL || '/api'
const api = axios.create({
  baseURL: baseURL,
  timeout: 10000
})

api.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    
    if (authStore.token) {
      config.headers['Authorization'] = `Bearer ${authStore.token}`
    }
    
    if (authStore.tenantId) {
      config.headers['X-Tenant-Id'] = authStore.tenantId
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          // 如果是登录请求，不显示默认错误消息，让登录页面处理
          if (error.config && error.config.url && error.config.url.includes('/auth/login')) {
            // 直接拒绝，让登录页面处理错误
            return Promise.reject(error)
          }
          const authStore = useAuthStore()
          authStore.logout()
          router.push('/login')
          ElMessage.error('登录已过期，请重新登录')
          break
        case 403:
          ElMessage.error('没有权限访问该资源')
          break
        case 500:
          ElMessage.error(error.response.data?.message || '服务器内部错误')
          break
        case 400:
          ElMessage.error(error.response.data?.message || '请求参数错误')
          break
        default:
          ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络设置')
    }
    return Promise.reject(error)
  }
)

export default api