import { Controller, useFormContext } from 'react-hook-form';
import {
  FormControlLabel,
  InputAdornment,
  MenuItem,
  Switch,
  TextField,
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';
import type { FieldConfig } from './fieldConfig';
import { ReferenceSelect } from './ReferenceSelect';
import { SubItemsEditor } from './SubItemsEditor';

interface Props {
  field: FieldConfig;
  namePrefix?: string;
  dense?: boolean;
}

const NUMERIC: FieldConfig['type'][] = ['number', 'integer', 'money', 'percent'];

export function FieldRenderer({ field, namePrefix = '', dense }: Props) {
  const { control } = useFormContext();
  const name = `${namePrefix}${field.name}`;
  const rules = { required: field.required ? 'Campo obrigatório' : false };
  const size = dense ? 'small' : 'small';

  if (field.type === 'subitems' && field.subFields) {
    return <SubItemsEditor name={name} label={field.label} subFields={field.subFields} />;
  }

  if (field.type === 'switch') {
    return (
      <Controller
        name={name}
        control={control}
        render={({ field: f }) => (
          <FormControlLabel
            control={<Switch checked={!!f.value} onChange={(e) => f.onChange(e.target.checked)} />}
            label={field.label}
          />
        )}
      />
    );
  }

  if (field.type === 'reference' && field.reference) {
    return (
      <Controller
        name={name}
        control={control}
        rules={rules}
        render={({ field: f, fieldState }) => (
          <ReferenceSelect
            label={field.label}
            value={f.value ?? null}
            onChange={f.onChange}
            reference={field.reference!}
            required={field.required}
            disabled={field.disabled}
            error={fieldState.error?.message}
          />
        )}
      />
    );
  }

  if (field.type === 'date') {
    return (
      <Controller
        name={name}
        control={control}
        rules={rules}
        render={({ field: f, fieldState }) => (
          <DatePicker
            label={field.label}
            value={f.value ? dayjs(f.value) : null}
            onChange={(d) => f.onChange(d && d.isValid() ? d.format('YYYY-MM-DD') : undefined)}
            slotProps={{
              textField: {
                fullWidth: true,
                size,
                required: field.required,
                error: !!fieldState.error,
                helperText: fieldState.error?.message || field.helperText,
              },
            }}
          />
        )}
      />
    );
  }

  if (field.type === 'select') {
    return (
      <Controller
        name={name}
        control={control}
        rules={rules}
        render={({ field: f, fieldState }) => (
          <TextField
            select
            fullWidth
            size={size}
            label={field.label}
            required={field.required}
            disabled={field.disabled}
            value={f.value ?? ''}
            onChange={(e) => f.onChange(e.target.value === '' ? undefined : e.target.value)}
            error={!!fieldState.error}
            helperText={fieldState.error?.message || field.helperText}
          >
            <MenuItem value="">
              <em>—</em>
            </MenuItem>
            {(field.options ?? []).map((opt) => (
              <MenuItem key={opt.value} value={opt.value}>
                {opt.label}
              </MenuItem>
            ))}
          </TextField>
        )}
      />
    );
  }

  const isNumeric = NUMERIC.includes(field.type);
  const isMoney = field.type === 'money';
  const isPercent = field.type === 'percent';

  return (
    <Controller
      name={name}
      control={control}
      rules={rules}
      render={({ field: f, fieldState }) => (
        <TextField
          fullWidth
          size={size}
          label={field.label}
          required={field.required}
          disabled={field.disabled}
          type={field.type === 'password' ? 'password' : isNumeric ? 'number' : 'text'}
          multiline={field.type === 'textarea'}
          minRows={field.type === 'textarea' ? 2 : undefined}
          value={f.value ?? ''}
          onChange={(e) => {
            const v = e.target.value;
            if (isNumeric) f.onChange(v === '' ? undefined : Number(v));
            else f.onChange(v);
          }}
          error={!!fieldState.error}
          helperText={fieldState.error?.message || field.helperText}
          inputProps={isNumeric ? { step: field.step ?? (field.type === 'integer' ? 1 : 0.01) } : undefined}
          InputProps={{
            startAdornment: isMoney ? <InputAdornment position="start">R$</InputAdornment> : undefined,
            endAdornment: isPercent ? <InputAdornment position="end">%</InputAdornment> : undefined,
          }}
        />
      )}
    />
  );
}
