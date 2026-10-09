/**
 * Price Compare Service — 跨平台比价透传（P1 六批）
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';

@Injectable()
export class PriceCompareService {
  private readonly logger = new Logger(PriceCompareService.name);

  constructor(private readonly http: HttpService) {}

  /** 跨平台比价：透传 product-service /price/compare（渠道为本地模拟源，生产可换真实 API） */
  async compare(userId: string, skuIds: number[]): Promise<any[]> {
    const url = `${SERVICES.productService.url}${SERVICE_PATHS.priceCompare}`;
    try {
      const resp = await firstValueFrom(
        this.http.post(url, skuIds, {
          headers: { 'x-user-id': userId, 'Content-Type': 'application/json' },
        }),
      );
      return resp.data?.data ?? [];
    } catch (err) {
      this.logger.warn(`product-service 比价失败: ${err instanceof Error ? err.message : String(err)}`);
      return [];
    }
  }
}
