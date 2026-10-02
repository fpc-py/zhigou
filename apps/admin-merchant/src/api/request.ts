/**
 * 商家后台 HTTP 请求封装
 */
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

http.interceptors.request.use((config) => {
  const userStore = useUserStore();
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      const userStore = useUserStore();
      userStore.clearToken();
      window.location.href = '/login';
      return Promise.reject(err);
    }
    const msg = err.response?.data?.message || '请求失败，请稍后重试';
    ElMessage.error(msg);
    return Promise.reject(err);
  },
);

export default http;