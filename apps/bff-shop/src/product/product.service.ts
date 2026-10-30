/**
 * Product Service — 商品聚合（详情 + 分页）
 */
import { Injectable, Logger, HttpException } from '@nestjs/common';
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

  /** 统一组装下游调用头：x-user-id 恒透传；x-merchant-id 仅商家账号（多商家隔离）透传 */
  private downstreamHeaders(userId: string, merchantId?: number | null): Record<string, string> {
    const headers: Record<string, string> = { 'x-user-id': userId };
    if (merchantId != null) headers['x-merchant-id'] = String(merchantId);
    return headers;
  }

  /**
   * 校验下游业务响应：非 0/200 业务码（如多商家越权 403）抛对应 HTTP 异常，
   * 避免把下游拒绝误报为成功。
   */
  private assertDownstreamOk(resp: any, action: string): void {
    const code = resp?.data?.code;
    if (code != null && code !== 0 && code !== 200) {
      const msg = resp?.data?.message || `product-service ${action} 被下游拒绝`;
      this.logger.warn(`product-service ${action} 业务拒绝: code=${code} msg=${msg}`);
      throw new HttpException(msg, code >= 400 && code < 600 ? Number(code) : 502);
    }
  }

  /** 商品分页（透传 product-service /product/page；商家账号带 x-merchant-id 按归属过滤） */
  async page(userId: string, query: {
    keyword?: string;
    pageNum?: number;
    pageSize?: number;
    categoryId?: number;
    brandId?: number;
  }, merchantId?: number | null): Promise<Record<string, any> | null> {
    const params = new URLSearchParams();
    if (query.keyword) params.set('keyword', query.keyword);
    params.set('pageNum', String(query.pageNum ?? 1));
    params.set('pageSize', String(query.pageSize ?? 20));
    if (query.categoryId != null) params.set('categoryId', String(query.categoryId));
    if (query.brandId != null) params.set('brandId', String(query.brandId));

    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productPage}?${params.toString()}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.downstreamHeaders(userId, merchantId) }).pipe(
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
    this.assertDownstreamOk(resp, 'GET /product/page');
    return resp.data?.data ?? null;
  }

  /** 新增商品（商家；merchantId 落库为归属商家，多商家隔离） */
  async createSpu(data: any, userId: string, merchantId?: number | null): Promise<string | null> {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productSpu}`;
    const resp = await firstValueFrom(
      this.http.post(url, data, { headers: this.downstreamHeaders(userId, merchantId) }).pipe(
        timeout(SERVICES.productService.timeout),
        catchError((err) => {
          this.logger.warn(`product-service POST /product/spu 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    this.assertDownstreamOk(resp, 'POST /product/spu');
    const spuId = resp.data?.data?.spuId;
    return spuId != null ? String(spuId) : null;
  }

  /** 更新商品（商家；merchantId 归属校验，非本商家商品拒绝） */
  async updateSpu(spuId: string, data: any, userId: string, merchantId?: number | null): Promise<boolean> {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productSpuItem(spuId)}`;
    const resp = await firstValueFrom(
      this.http.put(url, data, { headers: this.downstreamHeaders(userId, merchantId) }).pipe(
        timeout(SERVICES.productService.timeout),
        catchError((err) => {
          this.logger.warn(`product-service PUT /product/spu/${spuId} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    this.assertDownstreamOk(resp, `PUT /product/spu/${spuId}`);
    return resp.data?.data !== undefined && resp.data?.data !== null;
  }

  /** 下架商品（商家；merchantId 归属校验，非本商家商品拒绝） */
  async offShelf(spuId: string, userId: string, merchantId?: number | null): Promise<boolean> {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productSpuItem(spuId)}`;
    const resp = await firstValueFrom(
      this.http.delete(url, { headers: this.downstreamHeaders(userId, merchantId) }).pipe(
        timeout(SERVICES.productService.timeout),
        catchError((err) => {
          this.logger.warn(`product-service DELETE /product/spu/${spuId} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    this.assertDownstreamOk(resp, `DELETE /product/spu/${spuId}`);
    return resp.data?.data !== undefined && resp.data?.data !== null;
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