import { type ReactElement } from 'react';
import { type GridColDef } from '@mui/x-data-grid';
import { type FieldConfig, type ReferenceConfig } from '../form/fieldConfig';

export interface FilterConfig {
  name: string;
  label: string;
  type: 'text' | 'boolean' | 'select' | 'reference';
  options?: { value: string; label: string }[];
  reference?: ReferenceConfig;
}

export interface RowAction {
  key: string;
  label: string;
  icon?: ReactElement;
  color?: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'inherit';
  method: 'post' | 'put' | 'delete';
  /** Constroi o sufixo do path a partir da linha, ex.: (row) => `/${row.id}/baixa`. */
  pathSuffix: (row: Record<string, any>) => string;
  /** Mensagem de confirmacao (quando nao ha formulario). */
  confirm?: string;
  /** Campos para coletar um corpo/params antes de executar (ex.: baixa). */
  formFields?: FieldConfig[];
  /** Onde enviar os dados coletados pelo formulario. Default 'body'. */
  payloadAs?: 'body' | 'params';
  /** Predicado para exibir a acao apenas em certos estados. */
  visible?: (row: Record<string, any>) => boolean;
}

export interface ResourceConfig {
  key: string;
  basePath: string;
  singular: string;
  plural: string;
  subtitle?: string;
  columns: GridColDef[];
  fields: FieldConfig[];
  filters?: FilterConfig[];
  rowActions?: RowAction[];
  canCreate?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  defaultSort?: string;
  /** Converte a linha (Response) em valores iniciais do formulario de edicao. */
  toFormValues?: (row: Record<string, any>) => Record<string, unknown>;
}
