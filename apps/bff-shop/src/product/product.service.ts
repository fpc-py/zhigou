/**
 * Product Service — 并行聚合商品详情
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, TimeoutError } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { ProductDetailResponse } from './product.types.js';

@Injectable()
export class ProductService {
  private readonly logger = new Logger(ProductService.name);

  constructor(private readonly http: HttpService) {}

  async getDetail(spuId: string): Promise<ProductDetailResponse> {
    const [productResult, ragResult] = await Promise.all([
      this.safeCall<Record<string, any>>(this.fetchProduct(spuId)),
      this.safeCall<any>(this.fetchAiReason(spuId)),
    ]);

    return {
      product: productResult ?? null,
      aiReason: ragResult ?? null,
    };
  }

  private async fetchProduct(spuId: string) {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productDetail(spuId)}`;
    const obs = this.http.get(url).pipe(
      timeout(SERVICES.productService.timeout),
      catchError((err) => {
        if (err instanceof TimeoutError) {
          this.logger.warn(`product-service 超时: spuId=${spuId}`);
        } else {
          this.logger.warn(`product-service 失败: spuId=${spuId} ${err.message}`);
        }
        return Promise.resolve({ data: { data: null } });
      }),
    );
    const resp = await firstValueFrom(obs);
    return resp.data?.data ?? null;
  }

  private async fetchAiReason(spuId: string) {
    const url = `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.ragRetrieve}`;
    const obs = this.http.post(url, { query: spuId, top_k: 1 }).pipe(
      timeout(SERVICES.aiOrchestrator.timeout),
      catchError((err) => {
        this.logger.warn(`ai-orchestrator RAG 失败: spuId=${spuId} ${err.message}`);
        return Promise.resolve({ data: { items: [] } });
      }),
    );
    const resp = await firstValueFrom(obs);
    return resp.data?.items?.[0]?.text ?? null;
  }

  private async safeCall<T>(promise: Promise<T>): Promise<T | null> {
    try {
      return await promise;
    } catch {
      return null;
    }
  }
}