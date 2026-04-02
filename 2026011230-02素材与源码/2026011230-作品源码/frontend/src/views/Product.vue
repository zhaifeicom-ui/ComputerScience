<template>
  <div class="product-canvas">
    <div class="product-header">
      <div>
        <h1 class="product-title">商品管理</h1>
        <p class="product-subtitle">配置并管理您销售的所有商品，掌握数据脉络。</p>
      </div>

      <div class="product-toolbar">
        <div class="floating-search">
          <el-input
            v-model="searchQuery"
            placeholder="搜索商品名称、ID 或类目..."
            class="search-input"
            clearable
          >
            <template #prefix>
              <el-icon class="search-icon"><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <el-button class="primary-action !border-none" @click="openCreateDialog">
          <el-icon class="mr-1"><Plus /></el-icon> 新增商品
        </el-button>
      </div>
    </div>

    <div class="glass-table-card">
      <transition name="grid-fade" mode="out-in">
        <el-table
          :key="tableRenderKey"
          v-loading="loading"
          :data="filteredData"
          style="width: 100%"
          class="product-table"
          :row-key="(row: any) => row.id"
        >

          <el-table-column prop="name" label="商品名称" min-width="240">
            <template #default="scope">
              <span class="cell-name cursor-pointer hover:text-blue-500 transition-colors" @click="openSalesDataDialog(scope.row)">
                {{ scope.row.name }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="类目" width="170">
            <template #default="scope">
              <span class="category-badge" :style="getCategoryBadgeStyle(scope.row.category)">
                {{ scope.row.category }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="inventory" label="库存状态" width="140">
            <template #default="scope">
              <span v-if="getInventoryStatus(scope.row.id).exists" :class="getInventoryStatus(scope.row.id).class">
                {{ getInventoryStatus(scope.row.id).stock }}
                <span v-if="getInventoryStatus(scope.row.id).isOutOfStock" class="out-of-stock-indicator">
                  <el-icon class="mr-1"><Warning /></el-icon>缺货
                </span>
                <span v-else-if="getInventoryStatus(scope.row.id).isLowStock" class="low-stock-indicator">低库存</span>
              </span>
              <span v-else class="no-inventory">无库存记录</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="录入时间" width="200">
            <template #default="scope">
              <span class="cell-meta">{{ scope.row.createdAt }}</span>
            </template>
          </el-table-column>
          <el-table-column label="" width="230" fixed="right" align="right">
            <template #default="scope">
              <div class="row-actions">
                <el-button size="small" class="row-action-btn" @click="openAIPredictDialog(scope.row)">
                  预测
                </el-button>
                <el-button size="small" class="row-action-btn" @click="openEditDialog(scope.row)">
                  编辑
                </el-button>
                <el-popconfirm title="确定抹除此商品？" @confirm="handleDelete(scope.row.id)" confirm-button-type="danger" width="200">
                  <template #reference>
                    <el-button size="small" class="row-action-btn danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </transition>
    </div>

    <div class="pagination-wrap">
      <div class="pagination-island">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          @current-change="fetchData"
          class="island-pagination"
        />
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新增商品'"
      width="480px"
      class="!rounded-3xl"
      :show-close="false"
    >
      <div class="px-2 pt-2">
        <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
          <el-form-item label="商品名称" prop="name">
            <el-input v-model="form.name" placeholder="输入商品名称" size="large" class="!shadow-none" />
          </el-form-item>
          <el-form-item label="商品分类" prop="category">
            <el-input v-model="form.category" placeholder="输入商品分类" size="large" class="!shadow-none" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="flex justify-end space-x-3 px-2 pb-2">
          <el-button @click="dialogVisible = false" class="secondary-action !border-none">取消</el-button>
          <el-button class="primary-action !border-none" @click="handleSubmit" :loading="submitLoading">
            确认
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- AI预测对话框 -->
    <el-dialog
      v-model="aiDialogVisible"
      title="AI 销量预测"
      width="700px"
      class="!rounded-3xl"
      :show-close="false"
    >
      <div v-loading="aiLoading" element-loading-background="rgba(255, 255, 255, 0.8)" class="min-h-[200px] px-2 pt-2">
        <div class="mb-4 flex flex-wrap items-center gap-4">
          <div class="flex items-center">
            <span class="text-sm font-medium text-gray-700 w-20">预测天数：</span>
            <el-input-number v-model="aiPredictionDays" :min="1" :max="7" :step="1" class="!w-32" />
          </div>
          <div class="flex items-center">
            <span class="text-sm font-medium text-gray-700 w-20">模拟价格：</span>
            <el-input-number v-model="aiPredictionPrice" :min="0" :precision="2" :step="10" class="!w-32" placeholder="默认使用最新价" />
          </div>
          <el-button class="primary-action !border-none !ml-auto" @click="triggerAIPredict" :disabled="aiLoading">
            {{ aiPredictionResult ? '重新预测' : '开始预测' }}
          </el-button>
        </div>
        
        <div v-if="aiPredictionResult">
          <h3 class="text-gray-900 font-bold mb-4">预测结果 <span class="text-gray-500 font-normal">(未来 {{ aiPredictionDays }} 天)</span></h3>
          
          <div class="grid grid-cols-2 gap-4 mb-6">
            <div class="bg-gray-50 p-4 rounded-2xl">
              <div class="text-gray-500 text-xs mb-1">总预测销量</div>
              <div class="text-2xl font-bold text-gray-900">{{ Number(aiPredictionResult.totalPredictedQuantity || 0).toFixed(2) }}</div>
            </div>
            <div class="bg-gray-50 p-4 rounded-2xl">
              <div class="text-gray-500 text-xs mb-1">总预测销售额</div>
              <div class="text-2xl font-bold text-gray-900">￥{{ Number(aiPredictionResult.totalPredictedRevenue || 0).toFixed(2) }}</div>
            </div>
          </div>

          <div class="h-64 mb-6 w-full">
            <v-chart class="chart" :option="chartOption" autoresize />
          </div>
          
          <el-table :data="aiPredictionResult.predictions || []" style="width: 100%" max-height="250" class="!rounded-xl border border-gray-100">
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column label="预测销量" align="center">
              <template #default="scope">
                {{ Number(scope.row.predictedQuantity).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column label="上限" align="center">
              <template #default="scope">
                <span class="text-[#0071e3] font-medium">{{ Number(scope.row.upperBound).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="下限" align="center">
              <template #default="scope">
                <span class="text-[#86868b] font-medium">{{ Number(scope.row.lowerBound).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="预测销售额" align="right">
              <template #default="scope">
                ￥{{ Number(scope.row.predictedRevenue).toFixed(2) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-else-if="!aiLoading" class="text-center text-gray-500 py-12 flex flex-col items-center">
          <div class="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4">
            <el-icon :size="24" class="text-gray-400"><MagicStick /></el-icon>
          </div>
          <p class="mb-6 font-medium">配置预测天数，点击开始预测</p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end px-2 pb-2">
          <el-button @click="aiDialogVisible = false" class="secondary-action !border-none">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 商品销售数据弹窗 -->
    <el-dialog
      v-model="salesDialogVisible"
      :title="`${currentProductName} - 销售记录`"
      width="800px"
      class="!rounded-3xl"
    >
      <div v-loading="salesLoading" class="min-h-[300px] px-2 pt-2">
        <div class="flex justify-end mb-4">
          <el-radio-group v-model="salesViewType" size="small" class="!shadow-none">
            <el-radio-button value="table">表格视图</el-radio-button>
            <el-radio-button value="chart">图表视图</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="salesViewType === 'table'">
          <el-table 
            :data="salesData" 
            style="width: 100%;"
            class="!rounded-xl border border-gray-100"
            max-height="400"
          >
            <el-table-column prop="date" label="记录日期" width="160" />
            <el-table-column prop="sales" label="成交数量" width="120" align="center">
              <template #default="scope">
                <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-gray-50 text-gray-900">
                  {{ scope.row.sales }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="price" label="成交单价" width="140" align="right">
              <template #default="scope">
                <span v-if="scope.row.price">￥{{ Number(scope.row.price).toFixed(2) }}</span>
                <span class="text-gray-300" v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="交易总额" min-width="140" align="right">
              <template #default="scope">
                <span class="font-semibold">￥{{ (scope.row.sales * (scope.row.price || 0)).toFixed(2) }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="flex justify-center mt-4">
            <el-pagination
              v-model:current-page="salesCurrentPage"
              v-model:page-size="salesPageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="salesTotal"
              @size-change="handleSalesSizeChange"
              @current-change="handleSalesCurrentChange"
            />
          </div>
        </div>
        
        <div v-else class="h-80 w-full">
          <v-chart v-if="salesData.length > 0" class="chart w-full h-full" :option="salesChartOption" autoresize />
          <div v-else class="h-full flex items-center justify-center text-gray-400">暂无销售数据</div>
          
          <div class="flex justify-center mt-4">
            <el-pagination
              v-model:current-page="salesCurrentPage"
              v-model:page-size="salesPageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="salesTotal"
              @size-change="handleSalesSizeChange"
              @current-change="handleSalesCurrentChange"
            />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus, Search, MagicStick, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../utils/api'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent, LegendComponent, ToolboxComponent } from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  ToolboxComponent
])

const loading = ref(false)
const tableData = ref<any[]>([])
const inventoryMap = ref<Record<number, any>>({})
const searchQuery = ref('')
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)

// 销售数据弹窗相关
const salesDialogVisible = ref(false)
const salesLoading = ref(false)
const salesData = ref<any[]>([])
const currentProductId = ref<number | null>(null)
const currentProductName = ref('')
const salesCurrentPage = ref(1)
const salesPageSize = ref(10)
const salesTotal = ref(0)
const salesViewType = ref('table') // 'table' 或 'chart'

const salesChartOption = computed(() => {
  if (salesData.value.length === 0) return {}

  // 图表数据按日期升序排列
  const sortedData = [...salesData.value].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  const dates = sortedData.map(item => item.date)
  const sales = sortedData.map(item => item.sales)

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#e5e7eb',
      textStyle: { color: '#1d1d1f' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '16%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#86868b' }
    },
    yAxis: {
      type: 'value',
      name: '销量',
      nameTextStyle: { color: '#86868b' },
      splitLine: { lineStyle: { type: 'dashed', color: 'rgba(17, 24, 39, 0.1)' } },
      axisLabel: { color: '#86868b' }
    },
    series: [
      {
        name: '成交数量',
        type: 'bar',
        data: sales,
        itemStyle: {
          color: '#3b82f6',
          borderRadius: [4, 4, 0, 0]
        },
        barMaxWidth: 40
      }
    ]
  }
})

const formRef = ref()
const editId = ref<number | null>(null)

const aiDialogVisible = ref(false)
const aiLoading = ref(false)
const aiPredictionResult = ref<any>(null)
const aiPredictionDays = ref(7)
const aiPredictionPrice = ref<number | undefined>(undefined)
const currentPredictProduct = ref<any>(null)

const form = reactive({
  name: '',
  category: ''
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }]
}

const filteredData = computed(() => {
  if (!searchQuery.value) return tableData.value
  const query = searchQuery.value.toLowerCase()
  return tableData.value.filter((item: any) => 
    item.name?.toLowerCase().includes(query) || 
    item.category?.toLowerCase().includes(query) ||
    item.id?.toString().includes(query)
  )
})

const tableRenderKey = computed(() => `${currentPage.value}-${searchQuery.value}-${filteredData.value.length}`)

const chartOption = computed(() => {
  if (!aiPredictionResult.value || !aiPredictionResult.value.predictions) return {}
  
  const dates = aiPredictionResult.value.predictions.map((p: any) => p.date)
  const quantities = aiPredictionResult.value.predictions.map((p: any) => p.predictedQuantity)
  const lowers = aiPredictionResult.value.predictions.map((p: any) => p.lowerBound || p.predictedQuantity)
  const uppers = aiPredictionResult.value.predictions.map((p: any) => p.upperBound || p.predictedQuantity)
  
  return {
    legend: {
      data: ['预测销量', '上限', '下限'],
      top: 0,
      textStyle: { color: '#6e6e73', fontSize: 12 }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#e5e7eb',
      textStyle: { color: '#1d1d1f' },
      formatter: (params: any[]) => {
        const row = aiPredictionResult.value.predictions[params[0].dataIndex]
        return [
          `<div style="font-weight:600;margin-bottom:4px;">${row.date}</div>`,
          `<div>预测销量：${Number(row.predictedQuantity).toFixed(2)}</div>`,
          `<div>上限：${Number(row.upperBound).toFixed(2)}</div>`,
          `<div>下限：${Number(row.lowerBound).toFixed(2)}</div>`
        ].join('')
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '16%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#86868b' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: 'rgba(17, 24, 39, 0.1)' } },
      axisLabel: { color: '#86868b' }
    },
    series: [
      {
        name: '下限',
        type: 'line',
        data: lowers,
        smooth: true,
        showSymbol: false,
        lineStyle: { type: 'dashed', width: 1.5, color: 'rgba(0,113,227,0.45)' }
      },
      {
        name: '上限',
        type: 'line',
        data: uppers,
        smooth: true,
        showSymbol: false,
        lineStyle: { type: 'dashed', width: 1.5, color: 'rgba(0,113,227,0.45)' }
      },
      {
        name: '预测销量',
        type: 'line',
        data: quantities,
        smooth: true,
        showSymbol: false,
        itemStyle: { color: '#0071e3' },
        lineStyle: { width: 3, color: '#0071e3' }
      }
    ]
  }
})

const badgePalette = [
  { background: 'rgba(64, 156, 255, 0.14)', color: '#075fbf' },
  { background: 'rgba(175, 82, 222, 0.15)', color: '#7c2ab8' },
  { background: 'rgba(52, 199, 89, 0.15)', color: '#1f8a40' },
  { background: 'rgba(255, 149, 0, 0.15)', color: '#a85a00' },
  { background: 'rgba(90, 200, 250, 0.16)', color: '#116f8e' }
]

const getCategoryBadgeStyle = (category: string) => {
  const hash = Array.from(category || '').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return badgePalette[hash % badgePalette.length]
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await api.get(`/products?page=${currentPage.value}&size=${pageSize.value}`)
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
    
    // 获取库存数据
    try {
      const inventoryRes: any = await api.get('/inventories?page=1&size=1000')
      if (inventoryRes.code === 200 && inventoryRes.data) {
        const map: Record<number, any> = {}
        inventoryRes.data.records.forEach((inv: any) => {
          map[inv.productId] = inv
        })
        inventoryMap.value = map
      }
    } catch (error) {
      console.error('Failed to fetch inventory data:', error)
      // 不影响产品数据显示，只是库存状态不显示
    }
  } catch (error) {
    console.error('Failed to fetch products:', error)
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  isEdit.value = false
  editId.value = null
  Object.assign(form, {
    name: '',
    category: ''
  })
  dialogVisible.value = true
  // Reset validation after dialog opens
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

// 销售数据弹窗方法
const fetchSalesData = async () => {
  if (!currentProductId.value) return
  salesLoading.value = true
  try {
    const res: any = await api.get(`/sales?productId=${currentProductId.value}&page=${salesCurrentPage.value}&size=${salesPageSize.value}`)
    if (res.code === 200 && res.data) {
      salesData.value = res.data.records
      salesTotal.value = res.data.total
    }
  } catch (error) {
    console.error('获取销售数据失败:', error)
    ElMessage.error('获取销售数据失败')
  } finally {
    salesLoading.value = false
  }
}

const openSalesDataDialog = (row: any) => {
  currentProductId.value = row.id
  currentProductName.value = row.name
  salesCurrentPage.value = 1
  salesPageSize.value = 10
  salesDialogVisible.value = true
  fetchSalesData()
}

const handleSalesSizeChange = (val: number) => {
  salesPageSize.value = val
  salesCurrentPage.value = 1
  fetchSalesData()
}

const handleSalesCurrentChange = (val: number) => {
  salesCurrentPage.value = val
  fetchSalesData()
}

const openEditDialog = (row: any) => {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    name: row.name,
    category: row.category
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value && editId.value) {
          const res: any = await api.put(`/products/${editId.value}`, form)
          if (res.code === 200) {
            ElMessage.success('更新成功')
            dialogVisible.value = false
            fetchData()
          }
        } else {
          const res: any = await api.post('/products', form)
          if (res.code === 200) {
            ElMessage.success('创建成功')
            dialogVisible.value = false
            fetchData()
          }
        }
      } catch (error: any) {
        ElMessage.error(error.response?.data?.message || '操作失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDelete = async (id: number) => {
  try {
    const res: any = await api.delete(`/products/${id}`)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchData()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除失败')
  }
}

const openAIPredictDialog = async (row: any) => {
  currentPredictProduct.value = row
  aiPredictionResult.value = null
  aiPredictionDays.value = 7
  aiPredictionPrice.value = undefined
  aiDialogVisible.value = true

  // 尝试获取该商品最近一次销售记录的售价作为默认预测价格
  try {
    const res: any = await api.get(`/sales?productId=${row.id}&page=1&size=1`)
    if (res.code === 200 && res.data && res.data.records && res.data.records.length > 0) {
      // 假设接口返回的是按日期降序排列的，取第一条记录的价格
      const recentPrice = res.data.records[0].price
      if (recentPrice) {
        aiPredictionPrice.value = Number(recentPrice)
      }
    }
  } catch (error) {
    console.error('获取商品最新价格失败:', error)
  }
}

const triggerAIPredict = async () => {
  if (!currentPredictProduct.value) return
  
  // 校验输入价格是否在最近一次售价的 80% ~ 120% 之间（如果能获取到最近售价的话）
  if (aiPredictionPrice.value !== undefined) {
    try {
      const res: any = await api.get(`/sales?productId=${currentPredictProduct.value.id}&page=1&size=1`)
      if (res.code === 200 && res.data && res.data.records && res.data.records.length > 0) {
        const recentPrice = Number(res.data.records[0].price)
        if (recentPrice && recentPrice > 0) {
          const minPrice = recentPrice * 0.8
          const maxPrice = recentPrice * 1.2
          if (aiPredictionPrice.value < minPrice || aiPredictionPrice.value > maxPrice) {
            ElMessage.warning(`为了保证预测准确性，模拟价格需在最近售价的 80% - 120% 之间（即 ${minPrice.toFixed(2)} - ${maxPrice.toFixed(2)} 元）`)
            return
          }
        }
      }
    } catch (error) {
      console.error('校验价格范围失败:', error)
    }
  }

  aiLoading.value = true
  try {
    // 构造请求数据
    const requestData: any = {
      product_id: currentPredictProduct.value.id.toString(),
      days_to_forecast: aiPredictionDays.value,
      future_regressors: []
    }

    // 如果用户输入了模拟价格，则为未来的每一天构建 future_regressors
    if (aiPredictionPrice.value !== undefined && aiPredictionPrice.value !== null) {
      const today = new Date()
      const futureRegressors = []
      for (let i = 1; i <= aiPredictionDays.value; i++) {
        const futureDate = new Date(today)
        futureDate.setDate(today.getDate() + i)
        // 格式化为 YYYY-MM-DD
        const dateStr = futureDate.toISOString().split('T')[0]
        futureRegressors.push({
          ds: dateStr,
          price: aiPredictionPrice.value
        })
      }
      requestData.future_regressors = futureRegressors
    }

    const res: any = await api.post('/ai-predictions/predict/sales-forecast', requestData)
    if (res.code === 200 && res.data) {
      // 提取返回数据中的 predictions，处理可能存在的双层 data 嵌套情况
      const responseData = res.data.data ? res.data.data : res.data
      const predictions = responseData.predictions || []
      
      // 构建图表需要的数据结构，包含总计
      let totalQty = 0
      let totalRev = 0
      
      const formattedPredictions = predictions.map((p: any) => {
        // 如果后端返回了动态模拟的价格就用后端的，否则用前端输入的，都没有则预估为0不计算额度
        // 在新接口契约下，后端直接返回的是原始 dict 列表（包含 ds, yhat, yhat_lower, yhat_upper）
        const qty = p.yhat || p.predictedQuantity || 0
        const price = aiPredictionPrice.value !== undefined ? aiPredictionPrice.value : 0 // 简化：如果有模拟价格则算销售额
        
        totalQty += qty
        totalRev += qty * price

        return {
          date: p.ds || p.date,
          predictedQuantity: qty,
          upperBound: p.yhat_upper || p.upperBound || qty,
          lowerBound: p.yhat_lower || p.lowerBound || qty,
          predictedRevenue: qty * price
        }
      })
      
      aiPredictionResult.value = {
        totalPredictedQuantity: totalQty,
        totalPredictedRevenue: totalRev,
        predictions: formattedPredictions
      }
      
      ElMessage.success('预测完成')
    } else {
      ElMessage.error(res.message || '预测失败')
    }
  } catch (error: any) {
    console.error('预测失败:', error)
    ElMessage.error(error.response?.data?.message || '获取预测数据失败，请确保该商品有充足的历史销售记录')
  } finally {
    aiLoading.value = false
  }
}

const getInventoryStatus = (productId: number) => {
  const inventory = inventoryMap.value[productId]
  
  if (!inventory) {
    return {
      exists: false,
      stock: 0,
      isOutOfStock: false,
      isLowStock: false,
      class: 'inventory-status'
    }
  }
  
  const stock = inventory.stock || 0
  const safetyStock = inventory.safetyStock || 0
  
  const isOutOfStock = stock <= 0
  const isLowStock = !isOutOfStock && stock <= safetyStock
  
  let statusClass = 'inventory-status'
  if (isOutOfStock) {
    statusClass += ' out-of-stock'
  } else if (isLowStock) {
    statusClass += ' low-stock'
  } else {
    statusClass += ' normal-stock'
  }
  
  return {
    exists: true,
    stock,
    isOutOfStock,
    isLowStock,
    class: statusClass
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.product-canvas {
  background: #f5f5f7;
  border-radius: 28px;
  padding: 4px 2px 10px;
  font-family: Inter, 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Helvetica Neue', sans-serif;
}

.product-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  margin-bottom: 26px;
}

.product-title {
  font-size: 30px;
  line-height: 1.15;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: #1d1d1f;
}

.product-subtitle {
  margin-top: 10px;
  color: #86868b;
  font-size: 13px;
  letter-spacing: -0.01em;
}

.product-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
}

.floating-search {
  width: 300px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 14px;
  border: none;
  background: rgba(0, 0, 0, 0.04);
  box-shadow: 0 0 0 1px transparent inset;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.search-input :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #0071e3 inset, 0 0 0 4px rgba(0, 113, 227, 0.1);
}

.search-input :deep(.el-input__inner) {
  letter-spacing: -0.01em;
  color: #1d1d1f;
}

.search-input :deep(.el-input__inner::placeholder) {
  color: #86868b;
}

.search-icon {
  color: #86868b;
  transition: color 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.search-input :deep(.el-input__wrapper.is-focus) .search-icon {
  color: #0071e3;
}

.glass-table-card {
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  padding: 8px 12px;
}

.product-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-border-color: transparent;
  background: transparent;
}

.product-table :deep(.el-table__inner-wrapper::before),
.product-table :deep(.el-table__border-left-patch) {
  display: none;
}

.product-table :deep(.el-table__header th.el-table__cell) {
  background: transparent;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  color: #86868b;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  height: 56px;
}

.product-table :deep(.el-table__body td.el-table__cell) {
  background: transparent;
  border-bottom: 1px solid rgba(0, 0, 0, 0.045);
  border-right: none;
  height: 64px;
  letter-spacing: -0.015em;
  transition: background-color 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
  position: relative;
}

.product-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(0, 0, 0, 0.03);
}

.product-table :deep(.el-table__body td:first-child .cell) {
  padding-left: 22px;
}

.product-table :deep(.el-table__body td:first-child::before) {
  content: '';
  position: absolute;
  left: 8px;
  top: 50%;
  width: 3px;
  height: 16px;
  border-radius: 999px;
  background: #0071e3;
  transform: translateY(-50%) scaleY(0.6);
  opacity: 0;
  transition: transform 0.4s cubic-bezier(0.25, 0.1, 0.25, 1), opacity 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.product-table :deep(.el-table__body tr:hover td:first-child::before) {
  opacity: 1;
  transform: translateY(-50%) scaleY(1);
}

.product-table :deep(.el-table-fixed-column--right) {
  background: transparent;
}

.cell-id {
  color: #86868b;
  font-size: 12px;
  letter-spacing: 0.01em;
  font-variant-numeric: tabular-nums;
}

.cell-name {
  color: #1d1d1f;
  font-weight: 600;
  font-size: 15px;
}

.cell-meta {
  color: #86868b;
  font-size: 13px;
}

.category-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.range-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.12);
  color: #075fbf;
  font-size: 11px;
  font-weight: 600;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  opacity: 0;
  transform: translateX(8px);
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.product-table :deep(.el-table__body tr:hover .row-actions) {
  opacity: 1;
  transform: translateX(0);
}

.row-action-btn {
  border: none;
  background: transparent;
  color: #1d1d1f;
  border-radius: 999px;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.row-action-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.row-action-btn.danger {
  color: #d92d20;
}

.row-action-btn.danger:hover {
  background: rgba(217, 45, 32, 0.08);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 22px;
  padding-bottom: 8px;
}

.pagination-island {
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 999px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.04);
  padding: 8px 12px;
}

.island-pagination :deep(button),
.island-pagination :deep(.el-pager li) {
  background: transparent;
  border: none;
  border-radius: 999px;
  color: #86868b;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.island-pagination :deep(button:hover:not(:disabled)),
.island-pagination :deep(.el-pager li:hover) {
  background: rgba(0, 0, 0, 0.06);
  color: #1d1d1f;
}

.island-pagination :deep(.el-pager li.is-active) {
  background: #0071e3;
  color: #ffffff;
}

.primary-action {
  background: #0071e3;
  color: #ffffff;
  border-radius: 999px;
  padding: 0 20px;
  height: 38px;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.primary-action:hover {
  background: #0063c7;
}

.primary-action:active {
  transform: scale(0.96);
}

.secondary-action {
  background: rgba(0, 0, 0, 0.06);
  color: #1d1d1f;
  border-radius: 999px;
  padding: 0 20px;
  height: 38px;
  transition: all 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.secondary-action:hover {
  background: rgba(0, 0, 0, 0.1);
}

.secondary-action:active {
  transform: scale(0.96);
}

.grid-fade-enter-active,
.grid-fade-leave-active {
  transition: opacity 0.4s cubic-bezier(0.25, 0.1, 0.25, 1), transform 0.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.grid-fade-enter-from,
.grid-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.inventory-status {
  font-weight: 600;
  font-size: 14px;
  letter-spacing: -0.01em;
}

.out-of-stock {
  color: #d92d20;
}

.low-stock {
  color: #f76808;
}

.normal-stock {
  color: #1f8a40;
}

.out-of-stock-indicator {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 8px;
  background: rgba(217, 45, 32, 0.12);
  color: #d92d20;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
  100% {
    opacity: 1;
  }
}

.low-stock-indicator {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 8px;
  background: rgba(247, 104, 8, 0.12);
  color: #f76808;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.no-inventory {
  color: #86868b;
  font-size: 13px;
  font-style: italic;
}
</style>
