/**
 * Home Service — 并行聚合首页数据
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, TimeoutError } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { HomeFeedResponse, ProductItem } from './home.types.js';

@Injectable()
export class HomeService {
  private readonly logger = new Logger(HomeService.name);

  constructor(private readonly http: HttpService) {}

  async getFeed(userId: string): Promise<HomeFeedResponse> {
    const [productsResult, ragResult] = await Promise.all([
      this.safeCall<ProductItem[]>(this.fetchProducts()),
      this.safeCall<any[]>(this.fetchRagRecommend()),
    ]);

    return {
      banner: ['/banners/default-1.jpg', '/banners/default-2.jpg'], // 后续接营销 banner 接口
      recommend: ragResult ?? [],
      products: productsResult ?? [],
    };
  }

  // ── 下游调用封装 ──

  private async fetchProducts() {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productPage}?pageNum=1&pageSize=10`;
    const obs = this.http.get(url).pipe(
      timeout(SERVICES.productService.timeout),
      catchError((err) => {
        if (err instanceof TimeoutError) {
          this.logger.warn('product-service 超时');
        } else {
          this.logger.warn(`product-service 调用失败: ${err.message}`);
        }
        return Promise.resolve({ data: { data: { records: [] } } });
      }),
    );
    const resp = await firstValueFrom(obs);
    return resp.data?.data?.records ?? [];
  }

  private async fetchRagRecommend() {
    const url = `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.ragRetrieve}`;
    const obs = this.http.post(url, { query: '热门推荐', top_k: 6 }).pipe(
      timeout(SERVICES.aiOrchestrator.timeout),
      catchError((err) => {
        if (err instanceof TimeoutError) {
          this.logger.warn('ai-orchestrator RAG 超时');
        } else {
          this.logger.warn(`ai-orchestrator RAG 调用失败: ${err.message}`);
        }
        return Promise.resolve({ data: { items: [] } });
      }),
    );
    const resp = await firstValueFrom(obs);
    return resp.data?.items ?? [];
  }

  // ── 通用安全调用 ──

  private async safeCall<T>(promise: Promise<T>): Promise<T | null> {
    try {
      return await promise;
    } catch {
      return null;
    }
  }
}