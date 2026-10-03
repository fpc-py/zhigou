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

// ── 简易 toast（后续可替换为组件库 toast） ──
function showToast(msg: string) {
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
}

// ── 响应拦截器：业务码校验 + 401 清除 token（不跳转，防止死循环） ──
http.interceptors.response.use(
  (res: AxiosResponse) => {
    // BFF 在超时降级时会以 HTTP 200 返回 { code: 500, message: '服务超时', data: null }。
    // 若直接取 data，页面会拿到 null（如商品详情页 detail=null）并导致渲染崩溃，
    // 因此这里统一把业务码非 200 的响应转为失败，交给调用方 catch 处理。
    const body = res.data as { code?: number; message?: string } | null | undefined;
    if (body && typeof body.code === 'number' && body.code !== 200) {
      showToast(body.message || '系统繁忙，请稍后重试');
      return Promise.reject(new Error(body.message || `业务错误 ${body.code}`));
    }
    return res;
  },
  (err) => {
    if (err.response?.status === 401) {
      const userStore = useUserStore();
      userStore.clearToken();
      return Promise.reject(err);
    }
    showToast(err.response?.data?.message || '系统繁忙，请稍后重试');
    return Promise.reject(err);
  },
);

export default http;