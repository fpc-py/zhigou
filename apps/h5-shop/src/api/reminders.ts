import http from './request';

export interface ReminderItem {
  id: string;
  userId: string;
  type: 'price_drop' | 'repurchase' | 'season_outfit';
  title: string;
  detail: string;
  status: 'ACTIVE' | 'READ' | 'DISMISSED';
  createdAt: string;
  readAt?: string | null;
}

export async function listReminders(unreadOnly = false): Promise<ReminderItem[]> {
  const res = await http.get<{ code: number; data: ReminderItem[] }>('/reminders/mine', {
    params: { unreadOnly },
  });
  return res.data.data ?? [];
}

export async function scanReminders(): Promise<{ price_drop?: number; repurchase?: number; season_outfit?: number } | null> {
  const res = await http.post<{ code: number; data: { price_drop?: number; repurchase?: number; season_outfit?: number } | null }>('/reminders/scan', {});
  return res.data.data;
}

export async function markReminderRead(id: string): Promise<ReminderItem | null> {
  const res = await http.post<{ code: number; data: ReminderItem | null }>(`/reminders/read/${id}`, {});
  return res.data.data;
}
