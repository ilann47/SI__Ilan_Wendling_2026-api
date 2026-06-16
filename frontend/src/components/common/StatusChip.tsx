import { Chip } from '@mui/material';
import { statusColor } from '../../utils/format';

export function StatusChip({ status }: { status?: string | null }) {
  if (!status) return <span>—</span>;
  return <Chip size="small" label={status} color={statusColor(status)} />;
}
