<template>
  <div class="prod-card" @click="goDetail">
    <div class="prod-media">
      <img v-if="product.mainImage" :src="product.mainImage" :alt="product.name" loading="lazy" />
      <div v-else class="img-placeholder">🛍️</div>
      <span v-if="tagText" class="prod-tag" :class="tagClass">{{ tagText }}</span>
      <button class="prod-fav" @click.stop="toggleFav">
        <Icon name="heart" :class="{ faved: faved }" />
      </button>
    </div>
    <div class="prod-body">
      <h4 class="prod-name">{{ product.name }}</h4>
      <div class="prod-star">
        <Icon name="star" class="star-ic" />
        <span class="star-score">{{ score }}</span>
      </div>
      <div class="prod-foot">
        <p class="prod-price"><b>¥{{ priceText }}</b></p>
        <button class="add-btn" aria-label="加入购物车" @click.stop="onAdd">
          <Icon name="plus" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { addCart } from '@/api/cart';
import { showToast, formatPrice } from '@/utils';
import Icon from './Icon.vue';

const props = defineProps<{
  product: {
    spuId: string;
    name: string;
    mainImage?: string;
    priceMin: number;
    priceMax?: number;
    salesVolume?: number;
    subtitle?: string;
    skus?: Array<{ skuId: string; price?: number }>;
  };
}>();

const router = useRouter();
const userStore = useUserStore();
const faved = ref(false);
const adding = ref(false);

const priceText = computed(() => formatPrice(props.product.priceMin));
const score = computed(() => (4.5 + (Number(props.product.spuId) % 6) / 10).toFixed(1));

const tagText = computed(() => {
  if (props.product.subtitle) return props.product.subtitle;
  return '';
});
const tagClass = computed(() => (Number(props.product.spuId) % 3 === 0 ? 'pill-accent' : 'pill-mint'));

function goDetail() {
  router.push(`/product/${props.product.spuId}`);
}

function toggleFav() {
  faved.value = !faved.value;
  showToast(faved.value ? '已收藏' : '已取消收藏');
}

async function onAdd() {
  if (!userStore.isLoggedIn) {
    router.push('/login');
    return;
  }
  const skuId = props.product.skus?.[0]?.skuId;
  if (!skuId) {
    showToast('该商品暂无可售规格');
    return;
  }
  if (adding.value) return;
  adding.value = true;
  try {
    await addCart(skuId);
    showToast('已加入购物车');
  } catch {
    /* toast 已由 request 层提示 */
  } finally {
    adding.value = false;
  }
}
</script>

<style scoped>
.prod-card {
  background: var(--card);
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--sh-1);
  border: 1px solid var(--line);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  transition: transform var(--dur-mid) var(--ease-out), box-shadow var(--dur-mid) var(--ease-out);
  animation: zf-bounce-in .5s var(--ease-out) both;
}
.prod-card:hover { transform: translateY(-4px); box-shadow: var(--sh-2); }
.prod-card:active { transform: scale(.98); }
.prod-media {
  position: relative;
  aspect-ratio: 1;
  background: linear-gradient(135deg, #f6f7fb, #eef0f7);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.prod-media img { width: 100%; height: 100%; object-fit: cover; transition: transform var(--dur-mid) var(--ease-out); }
.prod-card:hover .prod-media img { transform: scale(1.05); }
.img-placeholder { font-size: 34px; opacity: 0.5; }
.prod-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  font-size: 10px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 999px;
}
.prod-fav {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-3);
  transition: transform var(--dur-fast) var(--ease-spring);
}
.prod-fav:active { transform: scale(1.3); }
.prod-fav .faved { color: var(--accent); fill: var(--accent); animation: zf-badge-pop .4s var(--ease-spring); }
.prod-body { padding: 10px 12px 12px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.prod-name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}
.prod-star { display: flex; align-items: center; gap: 4px; }
.star-ic { width: 13px; height: 13px; color: var(--amber); fill: var(--amber); }
.star-score { font-size: 11px; color: var(--ink-2); font-weight: 600; }
.prod-foot { display: flex; align-items: center; justify-content: space-between; margin-top: auto; }
.prod-price { color: var(--accent); font-size: 12px; font-weight: 600; }
.prod-price b { font-size: 17px; }
.add-btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--dur-fast) var(--ease-spring), background var(--dur-fast) var(--ease-out);
}
.add-btn:active { transform: scale(1.2); }
.add-btn svg { width: 15px; height: 15px; }
</style>
