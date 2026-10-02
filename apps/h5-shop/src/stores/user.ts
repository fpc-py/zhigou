import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '');
  const refreshTokenVal = ref(localStorage.getItem('refreshToken') || '');

  const isLoggedIn = computed(() => !!token.value);

  function setToken(access: string, refresh: string) {
    token.value = access;
    refreshTokenVal.value = refresh;
    localStorage.setItem('token', access);
    localStorage.setItem('refreshToken', refresh);
  }

  function clearToken() {
    token.value = '';
    refreshTokenVal.value = '';
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
  }

  return { token, refreshTokenVal, isLoggedIn, setToken, clearToken };
});