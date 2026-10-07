/** 售后单（与 aftersale-service 实体字段对齐） */
export interface AftersaleOrder {
  id: number;
  aftersaleNo: string;
  orderNo: string;
  userId: number;
  /** AFTERSALE / REFUND 等申请类型 */
  type: string;
  reason: string;
  /** 退款金额（分） */
  amount: number;
  status: 'APPLYING' | 'SELLER_APPROVED' | 'REFUNDING' | 'REFUNDED' | 'REJECTED' | 'CANCELED';
  images: string;
  rejectReason: string;
  skuId: number | null;
  count: number | null;
  refundNo: string;
  applyAt: string;
  finishAt: string;
}

export interface ApplyBody {
  orderNo: string;
  type: string;
  reason: string;
  amount: number;
  skuId?: number | null;
  count?: number | null;
  images?: string[];
}
