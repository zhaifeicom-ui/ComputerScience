<template>
  <div class="space-y-8">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-3xl font-bold text-apple-text tracking-tight">同步任务</h1>
        <p class="text-apple-gray mt-2 text-sm font-light">查看并管理您的数据同步进度。</p>
      </div>
      <el-button @click="fetchJobs" circle class="!bg-white/72 backdrop-blur-md !border-black/5 shadow-[0_8px_32px_0_rgba(0,0,0,0.04)] hover:shadow-md transition-all duration-300">
        <el-icon class="text-apple-gray"><Refresh /></el-icon>
      </el-button>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div v-for="job in jobs" :key="job.jobId" 
           class="glass-card p-6 group transition-all duration-500 ease-spring"
           :class="{'scale-up-breathe': job.status === 'COMPLETED' && job.isJustCompleted}">
        
        <div class="flex justify-between items-start mb-6">
          <div class="flex items-center">
              <div class="mr-4 w-12 h-12 rounded-[14px] bg-gray-50 flex items-center justify-center border border-black/5 transition-all duration-500 ease-spring">
                <el-icon :size="24" :class="getStatusColor(job.status)">
                  <Loading v-if="job.status === 'PROCESSING'" class="is-loading" />
                  <CircleCheckFilled v-else-if="job.status === 'COMPLETED'" class="animate-[draw_0.6s_ease-out_forwards]" />
                  <WarningFilled v-else-if="job.status === 'FAILED'" />
                  <Clock v-else />
                </el-icon>
              </div>
              <div>
                <h3 class="text-apple-text font-semibold text-base">{{ job.fileName || '未命名文件' }}</h3>
                <p class="text-xs text-apple-gray mt-1 uppercase tracking-wider font-light">任务编号 {{ job.jobId }}</p>
              </div>
            </div>
            <div class="flex items-center space-x-2">
              <span class="text-xs font-medium px-3 py-1 rounded-full transition-colors duration-300" :class="getStatusTagClass(job.status)">
                {{ getStatusText(job.status) }}
              </span>
              <el-popconfirm title="确认抹除此条记录？" @confirm="deleteJob(job.jobId)">
                <template #reference>
                  <el-button type="danger" link size="small" class="opacity-0 group-hover:opacity-100 transition-opacity duration-300 ease-spring p-0 h-auto">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>

        <div class="space-y-3">
          <div class="flex justify-between text-sm text-apple-gray font-medium">
            <span>进度</span>
            <span v-if="job.status === 'PROCESSING'">正在同步数据...</span>
            <span v-else-if="job.failureCount > 0" class="text-red-500">有 {{ job.failureCount }} 处细节需要调整</span>
            <span v-else class="text-green-600">已就绪</span>
          </div>
          
          <div class="w-full bg-gray-100 h-1 rounded-full overflow-hidden">
            <div class="h-full rounded-full transition-all duration-600 ease-spring"
                 :class="job.status === 'FAILED' ? 'bg-red-500' : (job.status === 'COMPLETED' ? 'bg-green-500' : 'bg-apple-blue')"
                 :style="{ width: `${job.progress || 0}%` }">
            </div>
          </div>
        </div>


      </div>

      <!-- 空状态设计 -->
      <div v-if="jobs.length === 0 && !loading" class="col-span-full py-32 flex flex-col items-center justify-center">
        <div class="w-24 h-24 rounded-full bg-white/50 border border-black/5 shadow-sm flex items-center justify-center mb-6">
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#d1d1d6" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="12" y1="12" x2="12" y2="18"></line>
            <line x1="9" y1="15" x2="15" y2="15"></line>
          </svg>
        </div>
        <p class="text-apple-text font-medium text-lg">选取 CSV 文件开始</p>
        <p class="text-apple-gray mt-2 font-light text-sm">前往销售流水页面导入您的数据</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Refresh, Loading, CircleCheckFilled, WarningFilled, Clock, Delete
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../utils/api'

const jobs = ref<any[]>([])
const loading = ref(false)
let timer: any = null

const fetchJobs = async () => {
  loading.value = true
  try {
    const res: any = await api.get('/jobs')
    if (res.code === 200 && res.data) {
      // 保留上一轮的状态来对比是否刚完成，触发呼吸动画
      const oldJobsMap = new Map(jobs.value.map(j => [j.jobId, j.status]))
      
      jobs.value = res.data.map((job: any) => ({
        ...job,
        isJustCompleted: oldJobsMap.has(job.jobId) && oldJobsMap.get(job.jobId) !== 'COMPLETED' && job.status === 'COMPLETED'
      }))
      
      const hasProcessing = jobs.value.some((job: any) => job.status === 'PROCESSING' || job.status === 'PENDING')
      if (hasProcessing && !timer) {
        timer = setInterval(fetchJobs, 2000)
      } else if (!hasProcessing && timer) {
        clearInterval(timer)
        timer = null
      }
    }
  } catch (error) {
    console.error('Failed to fetch jobs:', error)
  } finally {
    loading.value = false
  }
}

const getStatusColor = (status: string) => {
  switch (status) {
    case 'PENDING': return 'text-apple-gray'
    case 'PROCESSING': return 'text-apple-blue'
    case 'COMPLETED': return 'text-green-500'
    case 'FAILED': return 'text-red-500'
    default: return 'text-apple-gray'
  }
}

const getStatusTagClass = (status: string) => {
  switch (status) {
    case 'PENDING': return 'bg-gray-100 text-gray-600'
    case 'PROCESSING': return 'bg-blue-50 text-apple-blue'
    case 'COMPLETED': return 'bg-green-50 text-green-600'
    case 'FAILED': return 'bg-red-50 text-red-500'
    default: return 'bg-gray-100 text-gray-600'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'PENDING': return '等待中'
    case 'PROCESSING': return '处理中'
    case 'COMPLETED': return '已完成'
    case 'FAILED': return '已失败'
    default: return '未知'
  }
}



const deleteJob = async (jobId: string) => {
  try {
    const res: any = await api.delete(`/jobs/${jobId}`)
    if (res.code === 200) {
      ElMessage.success('已就绪。记录现已归位。')
      fetchJobs()
    } else {
      ElMessage.error(res.message || '有一处细节需要您的关注。')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '有一处细节需要您的关注。')
  }
}

onMounted(() => {
  fetchJobs()
})
</script>

<style scoped>
@keyframes draw {
  from {
    stroke-dasharray: 0, 100;
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    stroke-dasharray: 100, 100;
    opacity: 1;
    transform: scale(1);
  }
}
</style>
