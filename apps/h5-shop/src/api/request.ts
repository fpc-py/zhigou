/**
 * 统一 HTTP 请求封装
 * - 自动带 Authorization header
 * - 401 跳登录页
 * - 错误 toast
 */
import axios from 'axios';
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import { useUserStore } from '@/stores/user';

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// ── 请求拦截器：自动带 token ──
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const userStore = useUserStore();
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`;
  }
  return config;
});

// ── 响应拦截器：401 清除 token 但不跳转（防止死循环） ──
http.interceptors.response.use(
  (res: AxiosResponse) => res,
  (err) => {
    if (err.response?.status === 401) {
      const userStore = useUserStore();
      userStore.clearToken();
      return Promise.reject(err);
    }
    const msg = err.response?.data?.message || '系统繁忙，请稍后重试';
    // 简易 toast（后续可替换为组件库 toast）
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = msg;
    Object.assign(toast.style, {
      position: 'fixed', top: '60px', left: '50%', transform: 'translateX(-50%)',
      background: 'rgba(0,0,0,.8)', color: '#fff', padding: '10px 20px',
      borderRadius: '8px', zIndex: '9999', fontSize: '14px',
      maxWidth: '300px', textAlign: 'center',
    });
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2500);
    return Promise.reject(err);
  },
);

export default http;