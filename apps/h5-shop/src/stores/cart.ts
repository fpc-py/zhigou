import { defineStore } from 'pinia';
import { ref } from 'vue';

export interface CartItem {
  spuId: number;
  name: string;
  price: number;
  count: number;
  image?: string;
}

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>(JSON.parse(localStorage.getItem('cart') || '[]'));

  function addItem(item: CartItem) {
    const existing = items.value.find((i) => i.spuId === item.spuId);
    if (existing) {
      existing.count += item.count;
    } else {
      items.value.push(item);
    }
    save();
  }

  function removeItem(spuId: number) {
    items.value = items.value.filter((i) => i.spuId !== spuId);
    save();
  }

  function clearCart() {
    items.value = [];
    save();
  }

  function save() {
    localStorage.setItem('cart', JSON.stringify(items.value));
  }

  const totalCount = () => items.value.reduce((s, i) => s + i.count, 0);

  return { items, addItem, removeItem, clearCart, totalCount };
});