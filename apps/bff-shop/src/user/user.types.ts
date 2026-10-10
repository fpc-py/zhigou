/** /user /address 响应体类型（透传 user-service） */

export interface UserProfile {
  userId: string;
  phone: string;
  nickname: string;
  avatarUrl: string;
  gender: number;
  birthday?: string | null;
  level: number;
  point: number;
}

export interface UserProfileBody {
  nickname?: string;
  avatarUrl?: string;
  gender?: number;
  birthday?: string;
}

export interface AddressItem {
  addressId: string;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault: number;
}

export interface AddressBody {
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
}

/** 收藏 / 浏览条目（user-service 透传） */
export interface ProductTrackItem {
  id: string;
  spuId: string;
  skuId?: string;
  spuName: string;
  price?: number;
  imageUrl?: string;
  browseCount?: number;
  createTime?: string;
  lastBrowseTime?: string;
}

/** 收藏 / 浏览写请求 */
export interface ProductTrackBody {
  spuId: string;
  skuId?: string;
  spuName?: string;
  price?: number;
  imageUrl?: string;
}

/** 用户画像洞察（AI / 我的页展示） */
export interface UserInsight {
  favoriteCount: number;
  browseCount: number;
  browseTotal: number;
  topCategories: string[];
  priceBand: string;
  recentBrowse: ProductTrackItem[];
  note: string;
}

/** 收藏状态 */
export interface FavoriteCheck {
  favorited: boolean;
}
