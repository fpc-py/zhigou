/**
 * User Service — 用户资料 / 收货地址透传
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type {
  UserProfile,
  UserProfileBody,
  AddressItem,
  AddressBody,
  ProductTrackItem,
  ProductTrackBody,
  UserInsight,
  FavoriteCheck,
} from './user.types.js';

@Injectable()
export class UserService {
  private readonly logger = new Logger(UserService.name);

  constructor(private readonly http: HttpService) {}

  async getProfile(userId: string): Promise<UserProfile | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userProfile}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service /user/profile 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async updateProfile(userId: string, body: UserProfileBody): Promise<UserProfile | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userProfile}`;
    const resp = await firstValueFrom(
      this.http.put(url, body, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service PUT /user/profile 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  // ── 地址 ──

  async listAddresses(userId: string): Promise<AddressItem[]> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.addressList}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service /address/list 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  async addAddress(userId: string, body: AddressBody): Promise<AddressItem | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.addressRoot}`;
    const resp = await firstValueFrom(
      this.http.post(url, body, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service POST /address 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async updateAddress(userId: string, addressId: string, body: AddressBody): Promise<AddressItem | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.addressRoot}/${addressId}`;
    const resp = await firstValueFrom(
      this.http.put(url, body, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service PUT /address/${addressId} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async deleteAddress(userId: string, addressId: string): Promise<void> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.addressRoot}/${addressId}`;
    await firstValueFrom(
      this.http.delete(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service DELETE /address/${addressId} 失败: ${err.message}`);
          return Promise.resolve({ data: null });
        }),
      ),
    );
  }

  async setDefaultAddress(userId: string, addressId: string): Promise<void> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.addressDefault(addressId)}`;
    await firstValueFrom(
      this.http.put(url, {}, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service PUT /address/${addressId}/default 失败: ${err.message}`);
          return Promise.resolve({ data: null });
        }),
      ),
    );
  }

  // ── 收藏 ──

  async addFavorite(userId: string, body: ProductTrackBody): Promise<ProductTrackItem | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userFavorite}`;
    const resp = await firstValueFrom(
      this.http.post(url, body, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service POST /user/favorite 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async removeFavorite(userId: string, spuId: string): Promise<void> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userFavoriteDelete(spuId)}`;
    await firstValueFrom(
      this.http.delete(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service DELETE /user/favorite/${spuId} 失败: ${err.message}`);
          return Promise.resolve({ data: null });
        }),
      ),
    );
  }

  async listFavorites(userId: string, page = 1, size = 10): Promise<{ records: ProductTrackItem[]; total: number }> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userFavorite}?page=${page}&size=${size}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service GET /user/favorite 失败: ${err.message}`);
          return Promise.resolve({ data: { data: { records: [], total: 0 } } });
        }),
      ),
    );
    return resp.data?.data ?? { records: [], total: 0 };
  }

  async favoriteIds(userId: string): Promise<string[]> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userFavoriteIds}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service GET /user/favorite/ids 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  async checkFavorite(userId: string, spuId: string): Promise<FavoriteCheck> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userFavoriteCheck(spuId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service GET /user/favorite/check 失败: ${err.message}`);
          return Promise.resolve({ data: { data: { favorited: false } } });
        }),
      ),
    );
    return resp.data?.data ?? { favorited: false };
  }

  // ── 浏览历史 ──

  async recordBrowse(userId: string, body: ProductTrackBody): Promise<ProductTrackItem | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userBrowse}`;
    const resp = await firstValueFrom(
      this.http.post(url, body, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service POST /user/browse 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async recentBrowse(userId: string, limit = 20): Promise<ProductTrackItem[]> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userBrowseRecent(limit)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service GET /user/browse/recent 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  // ── 用户画像 ──

  async insight(userId: string): Promise<UserInsight | null> {
    const url = `${SERVICES.userService.url}${SERVICE_PATHS.userInsight}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.userService.timeout),
        catchError((err) => {
          this.logger.warn(`user-service GET /user/insight 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }
}
