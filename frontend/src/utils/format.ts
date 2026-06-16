import dayjs from 'dayjs';

export function formatCurrency(value?: number | null): string {
  if (value === null || value === undefined) return '—';
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function formatNumber(value?: number | null, digits = 0): string {
  if (value === null || value === undefined) return '—';
  return value.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

export function formatPercent(value?: number | null): string {
  if (value === null || value === undefined) return '—';
  return `${value.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—';
  const d = dayjs(iso);
  return d.isValid() ? d.format('DD/MM/YYYY') : '—';
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  const d = dayjs(iso);
  return d.isValid() ? d.format('DD/MM/YYYY HH:mm') : '—';
}

export function formatBool(value?: boolean | null): string {
  return value ? 'Sim' : 'Não';
}

export function minutesToHuman(min?: number | null): string {
  if (min === null || min === undefined) return '—';
  const h = Math.floor(min / 60);
  const m = Math.round(min % 60);
  return h === 0 ? `${m}min` : `${h}h${m.toString().padStart(2, '0')}`;
}

/** Cor (palette MUI) para uma situacao/status conhecido. */
export function statusColor(
  status?: string | null,
): 'default' | 'success' | 'warning' | 'error' | 'info' {
  switch (status) {
    case 'ATIVO':
    case 'CONFIRMADA':
    case 'EMITIDA':
    case 'PAGA':
    case 'RECEBIDA':
    case 'FECHADO':
      return 'success';
    case 'PENDENTE':
    case 'PARCIAL':
    case 'ABERTO':
      return 'warning';
    case 'CANCELADA':
    case 'CANCELADO':
      return 'error';
    case 'SUSPENSO':
      return 'info';
    default:
      return 'default';
  }
}
