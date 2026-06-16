import { useFieldArray, useFormContext } from 'react-hook-form';
import { Box, Button, IconButton, Paper, Stack, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import type { FieldConfig } from './fieldConfig';
import { FieldRenderer } from './FieldRenderer';
import { defaultValueFor } from './fieldConfig';

interface Props {
  name: string;
  label: string;
  subFields: FieldConfig[];
}

export function SubItemsEditor({ name, label, subFields }: Props) {
  const { control } = useFormContext();
  const { fields, append, remove } = useFieldArray({ control, name });

  const emptyRow = () => {
    const row: Record<string, unknown> = {};
    for (const sf of subFields) row[sf.name] = defaultValueFor(sf);
    return row;
  };

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="subtitle2">{label}</Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={() => append(emptyRow())}>
          Adicionar
        </Button>
      </Stack>
      <Stack spacing={1}>
        {fields.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            Nenhum item.
          </Typography>
        )}
        {fields.map((row, index) => (
          <Paper key={row.id} variant="outlined" sx={{ p: 1.5 }}>
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(12, 1fr)' },
                gap: 1.5,
                alignItems: 'center',
              }}
            >
              {subFields.map((sf) => (
                <Box key={sf.name} sx={{ gridColumn: { sm: `span ${sf.cols ?? 4}` } }}>
                  <FieldRenderer field={sf} namePrefix={`${name}.${index}.`} dense />
                </Box>
              ))}
              <Box sx={{ gridColumn: { sm: 'span 1' }, textAlign: 'right' }}>
                <IconButton color="error" onClick={() => remove(index)} aria-label="Remover">
                  <DeleteOutlineIcon />
                </IconButton>
              </Box>
            </Box>
          </Paper>
        ))}
      </Stack>
    </Box>
  );
}
