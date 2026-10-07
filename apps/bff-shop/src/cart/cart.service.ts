/**
 * Cart Service — 购物车聚合
 * cart-service 的 /cart/mine 仅返回 skuId/count/selected/priceAtAdd，
 * 这里并行聚合 product-service 的 SKU/SPU 信息（名称、规格、价格、图、库存）。
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, TimeoutError } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { CartItem } from './cart.types.js';

@Injectable()
export class CartService {
  private readonly logger = new Logger(CartService.name);

  constructor(private readonly http: HttpService) {}

  /** 我的购物车（聚合商品信息） */
  async mine(userId: string): Promise<CartItem[]> {
    const raw = await this.safeCall<CartItem[]>(this.fetchCartItems(userId));
    if (!raw || raw.length === 0) return [];

    // 并行：SPU 映射（skuId → name/mainImage）+ 各 SKU 详情
    const [spuMap, skuDetails] = await Promise.all([
      this.fetchSpuMap(userId),
      Promise.all(raw.map((item) => this.fetchSku(item.skuId, userId))),
    ]);

    return raw.map((item, i) => {
      const sku = skuDetails[i] ?? null;
      const spu = spuMap.get(item.skuId) ?? null;
      return {
        ...item,
        spuId: spu?.spuId,
        name: sku?.specName ? `${spu?.name ?? ''}`.trim() : spu?.name,
        specName: sku?.specName,
        specValue: sku?.specValue,
        price: sku?.price ?? item.priceAtAdd,
        stock: sku?.stock,
        image: sku?.image ?? spu?.mainImage,
      };
    });
  }

  async add(userId: string, body: { skuId: string; count?: number }): Promise<void> {
    const url = `${SERVICES.cartService.url}${SERVICE_PATHS.cartAdd}`;
    await this.post(url, userId, { skuId: body.skuId, count: body.count ?? 1 });
  }

  async update(userId: string, body: { skuId: string; count?: number; selected?: boolean }): Promise<void> {
    const url = `${SERVICES.cartService.url}${SERVICE_PATHS.cartUpdate}`;
    const payload: Record<string, unknown> = { skuId: body.skuId };
    if (body.count != null) payload.count = body.count;
    if (body.selected != null) payload.selected = body.selected;
    await this.post(url, userId, payload);
  }

  async clear(userId: string): Promise<void> {
    const url = `${SERVICES.cartService.url}${SERVICE_PATHS.cartClear}`;
    await this.post(url, userId, {});
  }

  async remove(userId: string, skuId: string): Promise<void> {
    const url = `${SERVICES.cartService.url}${SERVICE_PATHS.cartRemove(skuId)}`;
    try {
      await firstValueFrom(
        this.http.delete(url, { headers: { 'x-user-id': userId } }).pipe(
          timeout(SERVICES.cartService.timeout),
          catchError((err) => {
            this.logger.warn(`cart-service 删除失败 ${url}: ${err.message}`);
            return Promise.resolve({ data: null });
          }),
        ),
      );
    } catch (err: any) {
      this.logger.warn(`cart-service 删除异常 ${url}: ${err.message}`);
    }
  }

  // ── 下游调用 ──

  private async fetchCartItems(userId: string): Promise<CartItem[]> {
    const url = `${SERVICES.cartService.url}${SERVICE_PATHS.cartMine}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.cartService.timeout),
        catchError((err) => {
          this.logger.warn(`cart-service /cart/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  /** 拉取商品分页，构建 skuId → { spuId, name, mainImage } 映射 */
  private async fetchSpuMap(userId: string): Promise<Map<string, { spuId?: string; name?: string; mainImage?: string }>> {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productPage}?pageNum=1&pageSize=100`;
    const map = new Map<string, { spuId?: string; name?: string; mainImage?: string }>();
    try {
      const resp = await firstValueFrom(
        this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
          timeout(SERVICES.productService.timeout),
          catchError((err) => {
            this.logger.warn(`product-service /product/page 失败: ${err.message}`);
            return Promise.resolve({ data: { data: { records: [] } } });
          }),
        ),
      );
      const records: any[] = resp.data?.data?.records ?? [];
      for (const spu of records) {
        for (const sku of spu?.skus ?? []) {
          map.set(String(sku.skuId), {
            spuId: spu.spuId != null ? String(spu.spuId) : undefined,
            name: spu.name,
            mainImage: spu.mainImage,
          });
        }
      }
    } catch (err: any) {
      this.logger.warn(`fetchSpuMap 异常: ${err.message}`);
    }
    return map;
  }

  private async fetchSku(skuId: string, userId: string) {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.productSku(skuId)}`;
    try {
      const resp = await firstValueFrom(
        this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
          timeout(SERVICES.productService.timeout),
          catchError((err) => {
            this.logger.warn(`product-service /product/sku 失败 skuId=${skuId}: ${err.message}`);
            return Promise.resolve({ data: { data: null } });
          }),
        ),
      );
      return resp.data?.data ?? null;
    } catch {
      return null;
    }
  }

  private async post(url: string, userId: string, body: unknown): Promise<void> {
    await firstValueFrom(
      this.http.post(url, body, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.cartService.timeout),
        catchError((err) => {
          if (err instanceof TimeoutError) {
            this.logger.warn(`cart-service 超时: ${url}`);
          } else {
            this.logger.warn(`cart-service 调用失败 ${url}: ${err.message}`);
          }
          return Promise.resolve({ data: null });
        }),
      ),
    );
  }

  private async safeCall<T>(promise: Promise<T>): Promise<T | null> {
    try {
      return await promise;
    } catch {
      return null;
    }
  }
}
