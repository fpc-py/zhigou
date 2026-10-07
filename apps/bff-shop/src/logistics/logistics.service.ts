/**
 * Logistics Service — 运费计算透传
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { FreightCalculateBody, FreightCalculateResult } from './logistics.types.js';

@Injectable()
export class LogisticsService {
  private readonly logger = new Logger(LogisticsService.name);

  constructor(private readonly http: HttpService) {}

  async calculateFreight(body: FreightCalculateBody): Promise<FreightCalculateResult | null> {
    const url = `${SERVICES.logisticsSvc.url}${SERVICE_PATHS.freightCalculate}`;
    const resp = await firstValueFrom(
      this.http.post(url, body).pipe(
        timeout(SERVICES.logisticsSvc.timeout),
        catchError((err) => {
          this.logger.warn(`logistics-service /freight/calculate 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }
}
