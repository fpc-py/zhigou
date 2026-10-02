import http from './request';

export interface DashboardStats {
  todayGmv: number;
  orderCount: number;
  avgOrderAmount: number;
  refundRate: number;
}

/** 后端暂无统计接口，此函数为占位 */
export async function getDashboardStats(): Promise<DashboardStats> {
  // 尝试调用后端，失败则返回模拟数据
  try {
    const res = await http.get('/api/dashboard/stats');
    return res.data.data;
  } catch {
    return {
      todayGmv: 1280000,
      orderCount: 86,
      avgOrderAmount: 14884,
      refundRate: 2.3,
    };
  }
}