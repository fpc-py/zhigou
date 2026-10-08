/**
 * Product Service — 商品聚合（详情 + 分页）
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, TimeoutError } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { ProductDetailResponse } from './product.types.js';

@Injectable()
export class ProductService {
  private readonly logger = new Logger(ProductService.name);

  /** RAG 摘要内存缓存：同 spu 5 分钟内复用，避免详情接口每次都打 ai-orchestrator */
  private static readonly RAG_CACHE_TTL = 5 * 60_000;
  private readonly ragCache = new Map<string, { text: string; ts: number }>();

  constructor(private readonly http: HttpService) {}

  /** 商品分页（透传 product-service /product/page） */
  async page(userId: string, query: {
    keyword?: string;
    pageNum?: number;
    pageSize?: number;
    categoryId?: number;
    brandId?: number;
  }): Promise<Record<string, any> | null> {
    const params = new URLSearchParams();
    if (query.keyword) params.set('keyword', query.keyword);
    params.set('pageNum', String(query.pageNum ?? 1));
    params.set('pageSize', String(query.pageSize ?? 20));
    if (query.categoryId != null) params.set('categoryId', String(query.categoryId));
    if (query.brandId != null) params.set('brandId', String(query.brandId));

    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productPage}?${params.toString()}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.productService.timeout),
        catchError((err) => {
          if (err instanceof TimeoutError) {
            this.logger.warn('product-service /product/page 超时');
          } else {
            this.logger.warn(`product-service /product/page 失败: ${err.message}`);
          }
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async getDetail(spuId: string, userId: string): Promise<ProductDetailResponse> {
    const [productResult, ragResult] = await Promise.all([
      this.safeCall<Record<string, any>>(this.fetchProduct(spuId, userId)),
      this.safeCall<any>(this.fetchAiReason(spuId)),
    ]);

    return {
      product: productResult ?? null,
      aiReason: ragResult ?? null,
    };
  }

  private async fetchProduct(spuId: string, userId: string) {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productDetail(spuId)}`;
    const obs = this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
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
    const hit = this.ragCache.get(spuId);
    if (hit && Date.now() - hit.ts < ProductService.RAG_CACHE_TTL) return hit.text;
    const url = `${SERVICES.aiRag.url}${SERVICE_PATHS.ragRetrieve}`;
    const obs = this.http.post(url, { query: spuId, top_k: 1 }).pipe(
      // RAG 摘要快速失败（300ms）：AI 不可用时秒降级，不拖慢商品详情
      timeout(SERVICES.aiRag.timeout),
      catchError((err) => {
        this.logger.warn(`ai-orchestrator RAG 失败: spuId=${spuId} ${err.message}`);
        return Promise.resolve({ data: { items: [] } });
      }),
    );
    const resp = await firstValueFrom(obs);
    const text = resp.data?.items?.[0]?.text ?? null;
    if (text) this.ragCache.set(spuId, { text, ts: Date.now() });
    return text;
  }

  private async safeCall<T>(promise: Promise<T>): Promise<T | null> {
    try {
      return await promise;
    } catch {
      return null;
    }
  }
}