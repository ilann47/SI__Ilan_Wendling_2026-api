import { useEffect } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import { FormProvider, useForm } from 'react-hook-form';
import { type FieldConfig, defaultValueFor } from './fieldConfig';
import { FieldRenderer } from './FieldRenderer';

interface Props {
  open: boolean;
  title: string;
  fields: FieldConfig[];
  initialValues?: Record<string, unknown> | null;
  submitting?: boolean;
  onClose: () => void;
  onSubmit: (values: Record<string, unknown>) => void;
}

function buildDefaults(fields: FieldConfig[], initial?: Record<string, unknown> | null) {
  const out: Record<string, unknown> = {};
  for (const f of fields) {
    const v = initial?.[f.name];
    out[f.name] = v !== undefined && v !== null ? v : defaultValueFor(f);
  }
  return out;
}

function clean(values: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(values)) {
    if (v === '' || v === undefined) continue;
    out[k] = v;
  }
  return out;
}

export function ResourceFormDialog({
  open,
  title,
  fields,
  initialValues,
  submitting,
  onClose,
  onSubmit,
}: Props) {
  const methods = useForm<Record<string, unknown>>({ defaultValues: {} });

  useEffect(() => {
    if (open) methods.reset(buildDefaults(fields, initialValues));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = methods.handleSubmit((values) => onSubmit(clean(values)));

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <FormProvider {...methods}>
        <Box component="form" onSubmit={submit} noValidate>
          <DialogTitle>{title}</DialogTitle>
          <DialogContent dividers>
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(12, 1fr)' },
                gap: 2,
                pt: 1,
              }}
            >
              {fields.map((f) => {
                const cols = f.type === 'subitems' || f.type === 'textarea' ? 12 : f.cols ?? 6;
                return (
                  <Box key={f.name} sx={{ gridColumn: { sm: `span ${cols}` } }}>
                    <FieldRenderer field={f} />
                  </Box>
                );
              })}
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={onClose} color="inherit">
              Cancelar
            </Button>
            <Button type="submit" variant="contained" disabled={submitting}>
              Salvar
            </Button>
          </DialogActions>
        </Box>
      </FormProvider>
    </Dialog>
  );
}
