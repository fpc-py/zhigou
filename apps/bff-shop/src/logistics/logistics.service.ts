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

  /** 物流延误预警扫描：在途运单疑似停滞清单（用户侧物流管家） */
  async delayAlerts(stagnantHours = 48): Promise<any[] | null> {
    const url = `${SERVICES.logisticsSvc.url}${SERVICE_PATHS.logisticsDelayAlerts}?stagnantHours=${stagnantHours}`;
    const resp = await firstValueFrom(
      this.http.get(url).pipe(
        timeout(SERVICES.logisticsSvc.timeout),
        catchError((err) => {
          this.logger.warn(`logistics-service /logistics/delay-alerts 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  /** 一键调度：催件/重派/自提/改址/退换货（演示口径） */
  async dispatch(shipmentNo: string, action: string, reason?: string): Promise<any | null> {
    const url = `${SERVICES.logisticsSvc.url}${SERVICE_PATHS.logisticsDispatch}`;
    const resp = await firstValueFrom(
      this.http.post(url, { shipmentNo, action, reason }).pipe(
        timeout(SERVICES.logisticsSvc.timeout),
        catchError((err) => {
          this.logger.warn(`logistics-service /logistics/dispatch 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }
}
