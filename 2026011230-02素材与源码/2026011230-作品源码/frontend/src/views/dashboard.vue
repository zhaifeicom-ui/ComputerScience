<template>
  <div class="space-y-8">
    <div class="flex justify-between items-end">
      <div>
        <h1 class="text-3xl font-bold text-gray-900 tracking-tight">数据大盘</h1>
        <p class="text-gray-500 mt-2 text-sm font-medium flex items-center">
          <el-icon class="mr-1 text-gray-400"><TrendCharts /></el-icon>
          实时监控您的业务核心指标
        </p>
      </div>
      <div class="flex items-center space-x-6">
        <el-radio-group v-model="timeRange" size="default" @change="handleTimeRangeChange">
          <el-radio-button value="7">近7天</el-radio-button>
          <el-radio-button value="15">近15天</el-radio-button>
          <el-radio-button value="30">近30天</el-radio-button>
          <el-radio-button value="90">近3个月</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <el-button class="apple-btn !border-none" @click="router.push('/products')">
          <el-icon class="mr-1"><MagicStick /></el-icon> AI 预测分析
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <div v-for="(stat, index) in stats" :key="index" 
           class="bg-white rounded-3xl p-6 border border-gray-100 relative overflow-hidden group shadow-sm hover:shadow-xl transition-all duration-500 ease-spring"
           :style="{ animationDelay: `${index * 0.1}s` }"
           v-slide-in>
        
        <div class="flex justify-between items-start mb-6 relative z-10">
          <div class="p-3 rounded-2xl text-gray-700 bg-gray-50 group-hover:bg-black group-hover:text-white transition-colors duration-500">
            <el-icon :size="24"><component :is="stat.icon" /></el-icon>
          </div>
          <span class="flex items-center text-xs font-bold px-2.5 py-1 rounded-full" 
                :class="stat.isUp ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-500'">
            <el-icon class="mr-0.5"><Top v-if="stat.isUp"/><Bottom v-else/></el-icon>
            {{ stat.trend }}
          </span>
        </div>
        <h3 class="text-gray-500 text-sm font-medium">{{ stat.title }}</h3>
        <p class="text-3xl font-bold text-gray-900 mt-2 tracking-tight">{{ stat.value }}</p>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 bg-white rounded-3xl p-6 border border-gray-100 shadow-sm" v-slide-in style="animation-delay: 0.4s;">
        <h3 class="text-gray-900 font-bold mb-6 flex items-center text-lg">
          <el-icon class="mr-2 text-gray-400"><DataLine /></el-icon> 销售趋势 ({{ timeRange === 'all' ? '全部' : `近${timeRange}天` }})
        </h3>
        <div class="h-80 w-full">
          <v-chart class="chart" :option="lineOption" autoresize />
        </div>
      </div>

      <div class="bg-white rounded-3xl p-6 border border-gray-100 shadow-sm" v-slide-in style="animation-delay: 0.5s;">
        <h3 class="text-gray-900 font-bold mb-6 flex items-center text-lg">
          <el-icon class="mr-2 text-gray-400"><PieChart /></el-icon> 畅销商品分布
        </h3>
        <div class="h-80 w-full">
          <v-chart class="chart" :option="pieOption" autoresize />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart as EchartsPie } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import { 
  TrendCharts, MagicStick, Top, Bottom, 
  DataLine, PieChart, Money, ShoppingCart, Warning 
} from '@element-plus/icons-vue'

// Avoid unused warnings
console.log(Money, ShoppingCart, DataLine, Warning)
import api from '../utils/api'

// 注册 ECharts 组件
use([
  CanvasRenderer,
  LineChart,
  EchartsPie,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
])

const router = useRouter()

// 时间选择器
const timeRange = ref('7')

const handleTimeRangeChange = () => {
  fetchData()
}

// 模拟数据
const stats = ref([
  { title: '总销售额', value: '￥0', trend: '0%', isUp: true, icon: 'Money', color: 'from-blue-500 to-cyan-400' },
  { title: '订单总量', value: '0', trend: '0%', isUp: true, icon: 'ShoppingCart', color: 'from-purple-500 to-indigo-400' },
  { title: '活跃商品', value: '0', trend: '0%', isUp: false, icon: 'DataLine', color: 'from-geek-green to-emerald-400' },
  { title: '异常任务', value: '0', trend: '0%', isUp: true, icon: 'Warning', color: 'from-orange-500 to-amber-400' },
])

const lineOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    borderColor: '#e5e5ea',
    textStyle: { color: '#1d1d1f' }
  },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: [],
    axisLine: { lineStyle: { color: '#e5e5ea' } },
    axisLabel: { color: '#86868b' }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#f2f2f7', type: 'dashed' } },
    axisLabel: { color: '#86868b' }
  },
  series: [
    {
      name: '销售额',
      type: 'line',
      smooth: true,
      symbol: 'none',
      itemStyle: { color: '#00d1b2' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(0, 209, 178, 0.5)' },
            { offset: 1, color: 'rgba(0, 209, 178, 0)' }
          ]
        }
      },
      data: []
    }
  ]
})

const pieOption = ref({
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    borderColor: '#e5e5ea',
    textStyle: { color: '#1d1d1f' }
  },
  legend: {
    bottom: '5%',
    left: 'center',
    textStyle: { color: '#86868b' }
  },
  series: [
    {
      name: '商品占比',
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#ffffff',
        borderWidth: 2
      },
      label: { show: false },
      data: []
    }
  ]
})

const fetchData = async () => {
  try {
    const end = new Date()
    let startDate = ''
    const endDate = end.toISOString().split('T')[0]
    
    if (timeRange.value === 'all') {
      // 往前推10年，基本上涵盖了所有的可能数据范围
      const start = new Date()
      start.setFullYear(start.getFullYear() - 10)
      startDate = start.toISOString().split('T')[0]
    } else {
      const start = new Date()
      start.setDate(end.getDate() - parseInt(timeRange.value) + 1)
      startDate = start.toISOString().split('T')[0]
    }
    
    // 获取趋势数据
    const trendRes: any = await api.get(`/analysis/sales-trend?period=daily&startDate=${startDate}&endDate=${endDate}`)
    if (trendRes.code === 200 && trendRes.data) {
      lineOption.value.xAxis.data = trendRes.data.map((item: any) => item.date)
      lineOption.value.series[0].data = trendRes.data.map((item: any) => item.totalRevenue)
    }

    // 获取概览数据更新统计卡片
    const summaryRes: any = await api.get(`/analysis/sales-summary?startDate=${startDate}&endDate=${endDate}`)
    if (summaryRes.code === 200 && summaryRes.data) {
      const summary = summaryRes.data
      stats.value[0].value = `￥${Number(summary.totalRevenue || 0).toLocaleString('zh-CN', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`
      stats.value[1].value = Number(summary.totalSales || 0).toLocaleString()
      stats.value[2].value = summary.bestSellingProduct || '无'
      stats.value[3].value = Number(summary.averageDailySales || 0).toLocaleString()
    }

    // 获取畅销商品数据更新饼图
    const topRes: any = await api.get(`/analysis/top-products?period=daily&startDate=${startDate}&endDate=${endDate}&limit=5`)
    if (topRes.code === 200 && topRes.data) {
      pieOption.value.series[0].data = topRes.data.map((item: any) => ({
        name: item.productName,
        value: item.totalRevenue
      }))
    }
  } catch (error) {
    console.error('Failed to fetch dashboard data:', error)
  }
}

onMounted(() => {
  fetchData()
})

// 简单的滑入动画指令
const vSlideIn = {
  mounted: (el: HTMLElement) => {
    el.style.opacity = '0'
    el.style.transform = 'translateY(20px)'
    el.style.transition = 'all 0.6s cubic-bezier(0.16, 1, 0.3, 1)'
    
    setTimeout(() => {
      el.style.opacity = '1'
      el.style.transform = 'translateY(0)'
    }, 100)
  }
}
</script>