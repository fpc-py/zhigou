import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('admin_token') || '');
  const role = ref(localStorage.getItem('admin_role') || 'admin');

  const isLoggedIn = computed(() => !!token.value);

  function setToken(t: string) {
    token.value = t;
    localStorage.setItem('admin_token', t);
    localStorage.setItem('admin_role', 'admin');
    role.value = 'admin';
  }

  function clearToken() {
    token.value = '';
    role.value = '';
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_role');
  }

  return { token, role, isLoggedIn, setToken, clearToken };
});