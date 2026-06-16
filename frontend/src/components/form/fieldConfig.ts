export type FieldType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'integer'
  | 'money'
  | 'percent'
  | 'date'
  | 'switch'
  | 'select'
  | 'reference'
  | 'password'
  | 'subitems';

export interface SelectOption {
  value: string;
  label: string;
}

export interface ReferenceConfig {
  /** Endpoint do recurso referenciado, ex.: '/api/clientes'. */
  basePath: string;
  /** Campo do registro usado como rotulo da opcao. */
  labelField: string;
  /** Campo secundario opcional (mostrado apos um traco). */
  secondaryField?: string;
  /** Parametros extras de filtro na busca de opcoes. */
  params?: Record<string, unknown>;
}

export interface FieldConfig {
  name: string;
  label: string;
  type: FieldType;
  required?: boolean;
  /** Largura em colunas (1..12) no grid do formulario. Default 6. */
  cols?: number;
  options?: SelectOption[];
  reference?: ReferenceConfig;
  subFields?: FieldConfig[];
  helperText?: string;
  defaultValue?: unknown;
  disabled?: boolean;
  step?: number;
}

/** Valor inicial de um campo ao abrir o formulario em modo de criacao. */
export function defaultValueFor(field: FieldConfig): unknown {
  if (field.defaultValue !== undefined) return field.defaultValue;
  if (field.type === 'switch') return field.name === 'ativo' ? true : false;
  if (field.type === 'subitems') return [];
  return '';
}
