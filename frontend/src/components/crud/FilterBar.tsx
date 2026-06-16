import { useEffect, useState } from 'react';
import { Box, MenuItem, TextField } from '@mui/material';
import { ReferenceSelect } from '../form/ReferenceSelect';
import { type FilterConfig } from './resourceConfig';

interface Props {
  filters: FilterConfig[];
  onChange: (values: Record<string, unknown>) => void;
}

export function FilterBar({ filters, onChange }: Props) {
  const [local, setLocal] = useState<Record<string, unknown>>({});

  useEffect(() => {
    const timer = setTimeout(() => onChange(local), 400);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [local]);

  const set = (name: string, value: unknown) =>
    setLocal((prev) => ({ ...prev, [name]: value === '' ? undefined : value }));

  return (
    <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
      {filters.map((f) => {
        if (f.type === 'boolean') {
          return (
            <TextField
              key={f.name}
              select
              size="small"
              label={f.label}
              sx={{ minWidth: 150 }}
              value={(local[f.name] as string) ?? ''}
              onChange={(e) => set(f.name, e.target.value)}
            >
              <MenuItem value="">Todos</MenuItem>
              <MenuItem value="true">Sim</MenuItem>
              <MenuItem value="false">Não</MenuItem>
            </TextField>
          );
        }
        if (f.type === 'select') {
          return (
            <TextField
              key={f.name}
              select
              size="small"
              label={f.label}
              sx={{ minWidth: 170 }}
              value={(local[f.name] as string) ?? ''}
              onChange={(e) => set(f.name, e.target.value)}
            >
              <MenuItem value="">Todos</MenuItem>
              {(f.options ?? []).map((o) => (
                <MenuItem key={o.value} value={o.value}>
                  {o.label}
                </MenuItem>
              ))}
            </TextField>
          );
        }
        if (f.type === 'reference' && f.reference) {
          return (
            <Box key={f.name} sx={{ minWidth: 230 }}>
              <ReferenceSelect
                label={f.label}
                value={(local[f.name] as number) ?? null}
                onChange={(v) => set(f.name, v ?? '')}
                reference={f.reference}
              />
            </Box>
          );
        }
        return (
          <TextField
            key={f.name}
            size="small"
            label={f.label}
            value={(local[f.name] as string) ?? ''}
            onChange={(e) => set(f.name, e.target.value)}
          />
        );
      })}
    </Box>
  );
}
