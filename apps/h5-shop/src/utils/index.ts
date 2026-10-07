/**
 * 全局 Toast — 对齐原型的底部 toast 样式
 */
let toastEl: HTMLDivElement | null = null;
let timer: ReturnType<typeof setTimeout> | null = null;

export function showToast(msg: string, duration = 2000) {
  if (!toastEl) {
    toastEl = document.createElement('div');
    toastEl.id = 'toast';
    Object.assign(toastEl.style, {
      position: 'fixed',
      left: '50%',
      bottom: '96px',
      transform: 'translateX(-50%) translateY(16px)',
      background: 'rgba(16,20,36,.92)',
      color: '#fff',
      fontSize: '12.5px',
      padding: '11px 18px',
      borderRadius: '999px',
      zIndex: '999',
      opacity: '0',
      pointerEvents: 'none',
      transition: 'opacity .28s, transform .28s',
      boxShadow: '0 10px 30px rgba(0,0,0,.3)',
      maxWidth: '82vw',
      textAlign: 'center',
      lineHeight: '1.5',
    });
    document.body.appendChild(toastEl);
  }
  toastEl.textContent = msg;
  requestAnimationFrame(() => {
    if (toastEl) {
      toastEl.style.opacity = '1';
      toastEl.style.transform = 'translateX(-50%) translateY(0)';
    }
  });
  if (timer) clearTimeout(timer);
  timer = setTimeout(hideToast, duration);
}

export function hideToast() {
  if (!toastEl) return;
  toastEl.style.opacity = '0';
  toastEl.style.transform = 'translateX(-50%) translateY(16px)';
}

export function formatPrice(fen: number | undefined | null): string {
  const n = Number(fen || 0);
  return (n / 100).toFixed(2);
}

export function formatFen(fen: number | undefined | null): string {
  return formatPrice(fen);
}
