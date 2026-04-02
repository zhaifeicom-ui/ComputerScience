<template>
  <div class="inventory-canvas">
    <div class="inventory-header">
      <div>
        <h1 class="inventory-title">库存管理</h1>
        <p class="inventory-subtitle">管理商品库存，设置安全库存，监控库存状态。</p>
      </div>

      <div class="inventory-toolbar">
        <div class="floating-search">
          <el-input
            v-model="searchQuery"
            placeholder="搜索商品名称或ID..."
            class="search-input"
            clearable
          >
            <template #prefix>
              <el-icon class="search-icon"><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <el-button class="primary-action !border-none" @click="openCreateDialog">
          <el-icon class="mr-1"><Plus /></el-icon> 新增库存
        </el-button>
      </div>
    </div>

    <div class="glass-table-card">
      <el-table
        v-loading="loading"
        :data="filteredData"
        style="width: 100%"
        class="inventory-table"
        :row-key="(row: any) => row.id"
      >


        <el-table-column prop="productName" label="商品名称" min-width="200">
          <template #default="scope">
            <span class="cell-name">{{ scope.row.productName || '未命名商品' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="当前库存" width="140">
          <template #default="scope">
            <span :class="getStockStatusClass(scope.row)">{{ scope.row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="140">
          <template #default="scope">
            <span class="cell-meta">{{ scope.row.safetyStock }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="库存状态" width="140">
          <template #default="scope">
            <span :class="getStatusClass(scope.row)">{{ getStatusText(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestUpdateOn" label="最后更新时间" width="200">
          <template #default="scope">
            <span class="cell-meta">{{ formatDate(scope.row.latestUpdateOn) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="right">
          <template #default="scope">
            <div class="row-actions">
              <el-button size="small" class="row-action-btn" @click="openEditDialog(scope.row)">
                编辑
              </el-button>
              <el-popconfirm title="确定删除此库存记录？" @confirm="handleDelete(scope.row.id)" confirm-button-type="danger" width="200">
                <template #reference>
                  <el-button size="small" class="row-action-btn danger">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-wrap">
      <div class="pagination-island">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :before-close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="form-content"
      >
        <el-form-item label="商品名称" prop="productId">
          <el-select
            v-model="form.productId"
            placeholder="请选择商品"
            filterable
            class="w-full"
            :disabled="dialogType === 'edit'"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="product.name"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库存数量" prop="stock">
          <el-input
            v-model.number="form.stock"
            placeholder="请输入库存数量"
            type="number"
            min="0"
          />
        </el-form-item>
        <el-form-item label="安全库存" prop="safetyStock">
          <el-input
            v-model.number="form.safetyStock"
            placeholder="请输入安全库存"
            type="number"
            min="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ dialogType === 'create' ? '创建' : '更新' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import api from '../utils/api'

interface InventoryItem {
  id: number
  productId: number
  productName: string
  stock: number
  safetyStock: number
  latestUpdateOn: string
}

interface InventoryForm {
  productId: number | null
  stock: number | null
  safetyStock: number | null
}

const loading = ref(false)
const tableData = ref<InventoryItem[]>([])
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const dialogType = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => dialogType.value === 'create' ? '新增库存' : '编辑库存')
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<InventoryForm>({
  productId: null,
  stock: null,
  safetyStock: null
})

interface Product {
  id: number
  name: string
}

const products = ref<Product[]>([])

const currentId = ref<number | null>(null)

const rules: FormRules = {
  productId: [
    { required: true, message: '请选择商品', trigger: 'change' }
  ],
  stock: [
    { required: true, message: '请输入库存数量', trigger: 'blur' },
    { type: 'number', message: '库存数量必须为数字', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value < 0) {
        callback(new Error('库存数量不能小于0'))
      } else {
        callback()
      }
    }, trigger: 'blur' }
  ],
  safetyStock: [
    { required: true, message: '请输入安全库存', trigger: 'blur' },
    { type: 'number', message: '安全库存必须为数字', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value < 0) {
        callback(new Error('安全库存不能小于0'))
      } else {
        callback()
      }
    }, trigger: 'blur' }
  ]
}

const filteredData = computed(() => {
  if (!searchQuery.value) return tableData.value
  const query = searchQuery.value.toLowerCase()
  return tableData.value.filter(item =>
    item.productName?.toLowerCase().includes(query) ||
    item.productId?.toString().includes(query) ||
    item.id?.toString().includes(query)
  )
})

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await api.get('/inventories', {
      params: {
        page: currentPage.value,
        size: pageSize.value
      }
    })
    if (res.code === 200) {
      tableData.value = res.data.records || res.data
      total.value = res.data.total || res.data.length
    }
  } catch (error) {
    console.error('获取库存列表失败:', error)
    ElMessage.error('获取库存列表失败')
  } finally {
    loading.value = false
  }
}

const fetchProducts = async () => {
  try {
    const res: any = await api.get('/products/all')
    if (res.code === 200) {
      products.value = res.data
    }
  } catch (error) {
    console.error('获取商品列表失败:', error)
  }
}

const openCreateDialog = () => {
  dialogType.value = 'create'
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (item: InventoryItem) => {
  dialogType.value = 'edit'
  resetForm()
  currentId.value = item.id
  form.productId = item.productId
  form.stock = item.stock
  form.safetyStock = item.safetyStock
  dialogVisible.value = true
}

const resetForm = () => {
  currentId.value = null
  form.productId = null
  form.stock = null
  form.safetyStock = null
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    submitting.value = true
    
    if (dialogType.value === 'create') {
      const res: any = await api.post('/inventories', form)
      if (res.code === 200) {
        ElMessage.success('创建成功')
        dialogVisible.value = false
        fetchData()
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    } else {
      const res: any = await api.put(`/inventories/${currentId.value}`, form)
      if (res.code === 200) {
        ElMessage.success('更新成功')
        dialogVisible.value = false
        fetchData()
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    const res: any = await api.delete(`/inventories/${id}`)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchData()
    }
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败')
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

const handleDialogClose = (done: () => void) => {
  ElMessageBox.confirm('确定关闭吗？未保存的更改将丢失', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    done()
  }).catch(() => {})
}

const getStockStatusClass = (item: InventoryItem) => {
  if (item.stock === 0) return 'stock-out'
  if (item.stock < item.safetyStock) return 'stock-low'
  return 'stock-normal'
}

const getStatusText = (item: InventoryItem) => {
  if (item.stock === 0) return '缺货'
  if (item.stock < item.safetyStock) return '预警'
  return '充足'
}

const getStatusClass = (item: InventoryItem) => {
  if (item.stock === 0) return 'status-out'
  if (item.stock < item.safetyStock) return 'status-warning'
  return 'status-normal'
}

const formatDate = (dateString: string) => {
  if (!dateString) return '从未更新'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchData()
  fetchProducts()
})
</script>

<style scoped>
@reference "tailwindcss";

.inventory-canvas {
  @apply p-6 min-h-screen bg-gray-50;
}

.inventory-header {
  @apply flex flex-col lg:flex-row justify-between items-start lg:items-center mb-8;
}

.inventory-title {
  @apply text-2xl font-bold text-gray-800 mb-2;
}

.inventory-subtitle {
  @apply text-gray-500 text-sm;
}

.inventory-toolbar {
  @apply flex flex-col sm:flex-row items-start sm:items-center gap-4 mt-4 lg:mt-0;
}

.floating-search {
  @apply w-full sm:w-auto;
}

.search-input {
  @apply w-full sm:w-64;
}

.glass-table-card {
  @apply bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden mb-6;
}

.inventory-table {
  @apply w-full;
}

.row-actions {
  @apply flex justify-end gap-2;
}

.row-action-btn {
  @apply px-4 py-1 rounded-lg text-sm font-medium transition-colors;
}

.row-action-btn.danger {
  @apply text-red-600 bg-red-50 hover:bg-red-100;
}

.pagination-wrap {
  @apply flex justify-center mt-8;
}

.pagination-island {
  @apply bg-white rounded-xl shadow-sm border border-gray-100 px-4 py-2;
}

.stock-out {
  @apply text-red-600 font-semibold;
}

.stock-low {
  @apply text-amber-600 font-medium;
}

.stock-normal {
  @apply text-green-600 font-medium;
}

.status-out {
  @apply px-3 py-1 rounded-full text-xs font-medium bg-red-100 text-red-700;
}

.status-warning {
  @apply px-3 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-700;
}

.status-normal {
  @apply px-3 py-1 rounded-full text-xs font-medium bg-green-100 text-green-700;
}

.cell-id {
  @apply font-mono text-gray-700;
}

.cell-name {
  @apply font-medium text-gray-800;
}

.cell-meta {
  @apply text-gray-500 text-sm;
}

.form-content {
  @apply px-4;
}
</style>