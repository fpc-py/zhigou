<template>
  <div class="page address-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">收货地址</span>
      <span class="head-spacer" />
    </header>

    <Skeleton v-if="loading" w="100%" h="80px" :repeat="2" />
    <EmptyState v-else-if="list.length === 0" illustration="📍" text="还没有收货地址" />

    <div v-else class="addr-list">
      <div v-for="a in list" :key="a.addressId" class="addr-item card">
        <div class="addr-main" @click="select(a)">
          <div class="addr-row">
            <p class="addr-name">{{ a.receiverName }}</p>
            <p class="addr-phone">{{ a.receiverPhone }}</p>
            <span v-if="a.isDefault === 1" class="default-tag">默认</span>
          </div>
          <p class="addr-detail">{{ a.province }}{{ a.city }}{{ a.district }} {{ a.detail }}</p>
        </div>
        <div class="addr-ops">
          <button v-if="a.isDefault !== 1" class="op-btn" @click="setDefault(a)"><Icon name="check" size="xs" /> 设为默认</button>
          <button class="op-btn" @click="openEdit(a)"><Icon name="edit" size="xs" /> 编辑</button>
          <button class="op-btn danger" @click="remove(a)"><Icon name="refresh" size="xs" /> 删除</button>
        </div>
      </div>
    </div>

    <button class="add-btn" @click="openEdit(null)"><Icon name="plus" size="sm" /> 新增收货地址</button>

    <!-- 编辑弹层 -->
    <div v-if="editing" class="sheet-mask" @click.self="editing = false">
      <div class="sheet">
        <h3 class="sheet-title">{{ form.addressId ? '编辑地址' : '新增地址' }}</h3>
        <label class="field"><span>收货人</span><input v-model="form.receiverName" placeholder="姓名" /></label>
        <label class="field"><span>手机号</span><input v-model="form.receiverPhone" placeholder="11 位手机号" /></label>
        <div class="field-row">
          <label class="field"><span>省</span><input v-model="form.province" placeholder="省" /></label>
          <label class="field"><span>市</span><input v-model="form.city" placeholder="市" /></label>
          <label class="field"><span>区</span><input v-model="form.district" placeholder="区/县" /></label>
        </div>
        <label class="field"><span>详细地址</span><input v-model="form.detail" placeholder="街道、门牌号" /></label>
        <div class="sheet-ops">
          <button class="op cancel" @click="editing = false">取消</button>
          <button class="op ok" @click="save" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listAddresses, addAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/user';
import type { AddressItem, AddressBody } from '@/api/user';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const list = ref<AddressItem[]>([]);
const editing = ref(false);
const saving = ref(false);
const form = ref<AddressBody & { addressId?: string }>({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
});

async function load() {
  loading.value = true;
  try {
    list.value = await listAddresses();
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

function select(a: AddressItem) {
  const from = route.query.from;
  if (from === 'checkout') {
    router.replace({ path: '/checkout', query: { ...route.query, addressId: a.addressId } });
  }
}

function openEdit(a: AddressItem | null) {
  form.value = a
    ? { addressId: a.addressId, receiverName: a.receiverName, receiverPhone: a.receiverPhone, province: a.province, city: a.city, district: a.district, detail: a.detail }
    : { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detail: '' };
  editing.value = true;
}

async function save() {
  if (!form.value.receiverName || !form.value.receiverPhone) {
    showToast('请填写收货人与手机号');
    return;
  }
  saving.value = true;
  try {
    const body: AddressBody = {
      receiverName: form.value.receiverName,
      receiverPhone: form.value.receiverPhone,
      province: form.value.province,
      city: form.value.city,
      district: form.value.district,
      detail: form.value.detail,
    };
    if (form.value.addressId) {
      await updateAddress(form.value.addressId, body);
    } else {
      await addAddress(body);
    }
    editing.value = false;
    showToast('保存成功');
    load();
  } catch {
    /* 已提示 */
  } finally {
    saving.value = false;
  }
}

async function setDefault(a: AddressItem) {
  try {
    await setDefaultAddress(a.addressId);
    showToast('已设为默认');
    load();
  } catch {
    /* 已提示 */
  }
}

async function remove(a: AddressItem) {
  try {
    await deleteAddress(a.addressId);
    showToast('已删除');
    load();
  } catch {
    /* 已提示 */
  }
}

onMounted(load);
</script>

<style scoped>
.address-page { min-height: 100vh; padding: 0 14px 120px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px; margin-bottom: 10px; }
.addr-main { cursor: pointer; }
.addr-row { display: flex; align-items: center; gap: 8px; }
.addr-name { font-size: 14.5px; font-weight: 700; }
.addr-phone { font-size: 13px; color: var(--ink-2); }
.default-tag { font-size: 9.5px; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 2px 8px; font-weight: 600; }
.addr-detail { font-size: 12.5px; color: var(--ink-2); margin-top: 5px; line-height: 1.6; }
.addr-ops { display: flex; gap: 14px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--line); }
.op-btn { display: inline-flex; align-items: center; gap: 4px; font-size: 11.5px; color: var(--ink-2); }
.op-btn svg { color: var(--brand); }
.op-btn.danger svg { color: var(--danger); }
.add-btn {
  position: fixed;
  bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 40px);
  max-width: 374px;
  height: 46px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 14.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 10px 24px rgba(76, 92, 255, 0.35);
}
.sheet-mask { position: fixed; inset: 0; background: rgba(16, 20, 36, 0.45); z-index: 200; display: flex; align-items: flex-end; justify-content: center; }
.sheet { width: 100%; max-width: 414px; background: #fff; border-radius: 20px 20px 0 0; padding: 18px 16px calc(18px + env(safe-area-inset-bottom, 0px)); }
.sheet-title { font-size: 15px; font-weight: 700; margin-bottom: 14px; }
.field { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.field span { width: 58px; font-size: 12.5px; color: var(--ink-2); flex: none; }
.field input { flex: 1; height: 38px; background: var(--bg); border-radius: 10px; padding: 0 12px; font-size: 13.5px; }
.field-row { display: flex; gap: 6px; }
.field-row .field { flex: 1; }
.field-row .field span { width: 26px; }
.field-row .field input { width: 100%; min-width: 0; }
.sheet-ops { display: flex; gap: 10px; margin-top: 16px; }
.op { flex: 1; height: 42px; border-radius: 999px; font-size: 14px; font-weight: 600; }
.op.cancel { background: var(--bg); color: var(--ink-2); }
.op.ok { background: var(--brand); color: #fff; }
.op.ok:disabled { opacity: 0.5; }
</style>
