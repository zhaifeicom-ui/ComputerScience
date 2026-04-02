import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const tenantId = ref(localStorage.getItem('tenantId') || '')
  const username = ref(localStorage.getItem('username') || '')

  const setAuth = (newToken: string, newTenantId: string, newUsername: string) => {
    token.value = newToken
    tenantId.value = newTenantId
    username.value = newUsername
    
    localStorage.setItem('token', newToken)
    localStorage.setItem('tenantId', newTenantId)
    localStorage.setItem('username', newUsername)
  }

  const logout = () => {
    token.value = ''
    tenantId.value = ''
    username.value = ''
    
    localStorage.removeItem('token')
    localStorage.removeItem('tenantId')
    localStorage.removeItem('username')
  }

  return {
    token,
    tenantId,
    username,
    setAuth,
    logout
  }
})