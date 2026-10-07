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
