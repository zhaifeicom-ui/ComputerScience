<template>
  <div class="space-y-8">
    <div class="flex justify-between items-end">
      <div>
        <h1 class="text-3xl font-bold text-gray-900 tracking-tight uppercase">销售流水</h1>
        <p class="text-gray-500 mt-2 text-sm font-medium tracking-tight">查看并管理您的每一笔业务记录。</p>
      </div>
      
      <div class="flex items-center space-x-4">
        <!-- 苹果风搜索栏 -->
        <div class="relative group">
          <el-input
            v-model="searchQuery"
            placeholder="搜索商品名称..."
            class="w-72 !transition-all !duration-300"
            clearable
          >
            <template #prefix>
              <el-icon class="text-gray-400 group-focus-within:text-apple-blue transition-colors"><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <el-tooltip
          effect="light"
          placement="bottom-end"
          raw-content
        >
          <template #content>
            <div class="p-3 space-y-3 max-w-xs">
              <h4 class="font-bold text-gray-900 border-b border-gray-100 pb-2 mb-2">CSV 格式指引</h4>
              <p class="text-sm text-gray-600">为确保数据准确，首行需包含：</p>
              <ul class="text-sm text-gray-600 list-disc pl-5 space-y-1.5">
                <li><span class="text-black font-medium">日期</span>: date / ds / 日期</li>
                <li><span class="text-black font-medium">商品</span>: 商品名称 / product_name</li>
                <li><span class="text-black font-medium">销量</span>: sales / y / 销量</li>
                <li><span class="text-black font-medium">单价</span>: price / 价格 <span class="text-gray-400 text-xs">(选填)</span></li>
              </ul>
              <div class="mt-3 p-3 bg-gray-50 rounded-xl text-xs font-mono text-gray-500 border border-gray-100">
                示例:<br/>
                日期,商品名称,销量,单价<br/>
                2023-10-01,iPhone 15,10,7999.00
              </div>
            </div>
          </template>
          <div class="w-8 h-8 rounded-full bg-white flex items-center justify-center cursor-help shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
            <el-icon class="text-gray-400"><QuestionFilled /></el-icon>
          </div>
        </el-tooltip>
        
        <el-button @click="openSingleSaleDialog" class="apple-btn-secondary !border-none">
          <el-icon class="mr-1"><Plus /></el-icon> 单条录入
        </el-button>
        <el-upload
          class="inline-block"
          action="#"
          :show-file-list="false"
          :auto-upload="false"
          :on-change="handleFileUpload"
        >
          <el-button class="apple-btn !border-none">
            <el-icon class="mr-1"><Upload /></el-icon> 批量导入
          </el-button>
        </el-upload>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="glass-card p-2 overflow-hidden bg-white/80">
      <el-table 
        v-loading="loading" 
        :data="filteredData" 
        style="width: 100%;"
        class="!rounded-2xl !bg-transparent"
        :row-key="getSalesRowKey"
      >
        <el-table-column prop="date" label="记录日期" width="160">
          <template #default="scope">
            <span class="text-apple-gray text-sm tracking-tight">{{ scope.row.date }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品信息" min-width="180">
          <template #default="scope">
            <span class="font-semibold text-apple-text tracking-tight text-[15px]">{{ scope.row.productName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="成交数量" width="120" align="center">
          <template #default="scope">
            <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-gray-50 text-apple-text tracking-tight">
              {{ scope.row.sales }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="成交单价" width="140" align="right">
          <template #default="scope">
            <span class="text-apple-gray font-medium text-sm" v-if="scope.row.price">￥{{ Number(scope.row.price).toFixed(2) }}</span>
            <span class="text-gray-300" v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="交易总额" width="140" align="right">
          <template #default="scope">
            <span class="font-semibold text-apple-text tracking-tight">￥{{ (scope.row.sales * (scope.row.price || 0)).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 悬浮小岛分页器 -->
    <div class="flex justify-center mt-8 pb-4">
      <div class="glass-card !rounded-full px-6 py-3 inline-flex items-center space-x-4 shadow-[0_8px_32px_rgba(0,0,0,0.06)] bg-white/90">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="!bg-transparent"
        />
      </div>
    </div>

    <!-- 单条导入对话框 -->
    <el-dialog
      v-model="singleSaleDialogVisible"
      title="单条销售数据导入"
      width="500px"
      class="!rounded-3xl"
    >
      <el-form :model="singleSaleForm" :rules="singleSaleRules" ref="singleSaleFormRef" label-width="100px">
        <el-form-item label="选择商品" prop="productId">
          <el-select v-model="singleSaleForm.productId" placeholder="请选择商品" class="w-full" filterable>
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="product.name"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="销售日期" prop="date">
          <el-date-picker
            v-model="singleSaleForm.date"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="销售数量" prop="sales">
          <el-input-number v-model="singleSaleForm.sales" :min="1" class="w-full" />
        </el-form-item>
        <el-form-item label="销售单价" prop="price">
          <el-input-number v-model="singleSaleForm.price" :min="0.01" :precision="2" :step="0.1" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="singleSaleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSingleSaleSubmit" :loading="submitLoading">
            确认导入
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Upload, Search, Plus, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
// import confetti from 'canvas-confetti'
import api from '../utils/api'

const loading = ref(false)
const searchQuery = ref('')
const tableData = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

// 单条导入相关
const singleSaleDialogVisible = ref(false)
const submitLoading = ref(false)
const singleSaleFormRef = ref()
const products = ref<any[]>([])

const singleSaleForm = reactive({
  productId: null,
  date: new Date().toISOString().split('T')[0],
  sales: 1,
  price: 0
})

const singleSaleRules = {
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  sales: [{ required: true, message: '请输入销售数量', trigger: 'blur' }],
  price: [{ required: true, message: '请输入销售单价', trigger: 'blur' }]
}

const filteredData = computed(() => {
  if (!searchQuery.value) return tableData.value
  const query = searchQuery.value.toLowerCase()
  return tableData.value.filter((item: any) => 
    item.productName?.toLowerCase().includes(query)
  )
})

const getSalesRowKey = (row: any) => row.id || `${row.date}-${row.productName}`

const fetchProducts = async () => {
  try {
    const res: any = await api.get('/products?page=1&size=1000') // 获取所有商品
    if (res.code === 200 && res.data && res.data.records) {
      // 提取中文拼音首字母排序
      products.value = res.data.records.sort((a: any, b: any) => {
        return a.name.localeCompare(b.name, 'zh-CN')
      })
    }
  } catch (error) {
    console.error('Failed to fetch products:', error)
  }
}

const openSingleSaleDialog = () => {
  singleSaleForm.productId = null
  singleSaleForm.date = new Date().toISOString().split('T')[0]
  singleSaleForm.sales = 1
  singleSaleForm.price = 0
  singleSaleDialogVisible.value = true
  if (products.value.length === 0) {
    fetchProducts()
  }
}

const handleSingleSaleSubmit = async () => {
  if (!singleSaleFormRef.value) return
  await singleSaleFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      submitLoading.value = true
      try {
        const res: any = await api.post('/jobs/single-sale', singleSaleForm)
        if (res.code === 200) {
          ElMessage.success('导入成功')
          singleSaleDialogVisible.value = false
          fetchData()
        } else {
          // 这里可以不写 ElMessage.error，因为拦截器里会处理
          // 但为了保险也可以写上
          if(res.message) ElMessage.error(res.message)
        }
      } catch (error: any) {
        // 全局拦截器已经弹出了错误，这里只做 console 打印或者清理 loading 状态即可
        console.error('导入单条销售数据失败:', error)
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await api.get(`/sales?page=${currentPage.value}&size=${pageSize.value}`)
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records.map((item: any) => ({
        ...item,
        date: item.date,
        sales: item.sales
      }))
      total.value = res.data.total
    }
  } catch (error) {
    console.error('Failed to fetch sales data:', error)
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  fetchData()
}

const handleFileUpload = async (file: any) => {
  const formData = new FormData()
  formData.append('file', file.raw)
  
  ElMessage.info('正在同步您的销售数据...')
  
  try {
    const res: any = await api.post('/jobs/upload-data', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    if (res.code === 200) {
      triggerConfetti()
      ElMessage.success({
        message: '已就绪。数据现已归位。',
        duration: 3000
      })
    } else {
      if(res.message) ElMessage.error(res.message)
    }
  } catch (error: any) {
    console.error('上传文件失败:', error)
  }
}

const triggerConfetti = () => {
  const duration = 3 * 1000;
  const end = Date.now() + duration;

  const frame = () => {
    import('canvas-confetti').then((confettiModule) => {
      const confetti = confettiModule.default;
      confetti({
        particleCount: 5,
        angle: 60,
        spread: 55,
        origin: { x: 0 },
        colors: ['#00d1b2', '#3b82f6']
      });
      confetti({
        particleCount: 5,
        angle: 120,
        spread: 55,
        origin: { x: 1 },
        colors: ['#00d1b2', '#3b82f6']
      });
    });

    if (Date.now() < end) {
      requestAnimationFrame(frame);
    }
  };
  frame();
}

onMounted(() => {
  fetchData()
})
</script>
