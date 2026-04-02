import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../views/register.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: () => import('../layout/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('../views/dashboard.vue')
        },
        {
          path: 'products',
          name: 'Products',
          component: () => import('../views/Product.vue')
        },
        {
          path: 'sales',
          name: 'Sales',
          component: () => import('../views/Sales.vue')
        },
        {
          path: 'jobs',
          name: 'Jobs',
          component: () => import('../views/Jobs.vue')
        },
        {
          path: 'inventories',
          name: 'Inventories',
          component: () => import('../views/Inventory.vue')
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  
  if (to.meta.requiresAuth && !authStore.token) {
    next({ name: 'Login' })
  } else if ((to.name === 'Login' || to.name === 'Register') && authStore.token) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router