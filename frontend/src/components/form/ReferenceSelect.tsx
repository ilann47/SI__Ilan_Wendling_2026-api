import { useQuery } from '@tanstack/react-query';
import { Autocomplete, CircularProgress, TextField } from '@mui/material';
import { api } from '../../api/client';
import type { Page } from '../../api/resource';
import type { ReferenceConfig } from './fieldConfig';

interface RefOption {
  id: number;
  [key: string]: unknown;
}

interface Props {
  label: string;
  value: number | null | undefined;
  onChange: (value: number | null) => void;
  reference: ReferenceConfig;
  required?: boolean;
  error?: string;
  disabled?: boolean;
}

function optionLabel(option: RefOption, ref: ReferenceConfig): string {
  const main = option[ref.labelField];
  const text = main === undefined || main === null ? `#${option.id}` : String(main);
  if (ref.secondaryField) {
    const sec = option[ref.secondaryField];
    if (sec !== undefined && sec !== null && sec !== '') return `${text} — ${sec}`;
  }
  return text;
}

export function ReferenceSelect({ label, value, onChange, reference, required, error, disabled }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['reference', reference.basePath, reference.params],
    queryFn: () =>
      api
        .get<Page<RefOption>>(reference.basePath, { params: { size: 200, ...reference.params } })
        .then((r) => r.data.content),
    staleTime: 60_000,
  });

  const options = data ?? [];
  const selected = options.find((o) => o.id === value) ?? null;

  return (
    <Autocomplete
      fullWidth
      size="small"
      options={options}
      loading={isLoading}
      value={selected}
      disabled={disabled}
      noOptionsText="Nenhum registro"
      loadingText="Carregando..."
      getOptionLabel={(o) => optionLabel(o, reference)}
      isOptionEqualToValue={(o, v) => o.id === v.id}
      onChange={(_, v) => onChange(v ? v.id : null)}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          required={required}
          error={!!error}
          helperText={error}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {isLoading ? <CircularProgress color="inherit" size={16} /> : null}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
    />
  );
}
