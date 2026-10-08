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
    const [productsResult, ragResult, personalResult] = await Promise.all([
      this.safeCall<ProductItem[]>(this.fetchProducts(userId)),
      this.safeCall<any[]>(this.fetchRagRecommend()),
      this.safeCall<any>(this.fetchPersonalRecommend(userId)),
    ]);

    return {
      banner: ['/banners/default-1.jpg', '/banners/default-2.jpg'], // 后续接营销 banner 接口
      recommend: ragResult ?? [],
      products: productsResult ?? [],
      personal: personalResult ?? undefined,
    };
  }

  // ── 下游调用封装 ──

  private async fetchProducts(userId: string) {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productPage}?pageNum=1&pageSize=10`;
    const obs = this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
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

  /** 个性化推荐：product-service /recommend（画像 = 订单 + 购物车 + 评价口碑） */
  private async fetchPersonalRecommend(userId: string) {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productRecommend}?scene=home&limit=6`;
    const obs = this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
      timeout(SERVICES.productService.timeout + 2_000),
      catchError((err) => {
        if (err instanceof TimeoutError) {
          this.logger.warn('product-service 个性化推荐超时');
        } else {
          this.logger.warn(`product-service 个性化推荐失败: ${err.message}`);
        }
        return Promise.resolve({ data: { data: null } });
      }),
    );
    const resp = await firstValueFrom(obs);
    return resp.data?.data ?? null;
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