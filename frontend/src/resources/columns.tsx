import { type GridColDef } from '@mui/x-data-grid';
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatNumber,
  formatPercent,
} from '../utils/format';
import { StatusChip } from '../components/common/StatusChip';

type Extra = Partial<GridColDef>;

/** Construtores de coluna para o DataGrid, com formatacao PT-BR. */
export const cols = {
  id: (extra: Extra = {}): GridColDef => ({ field: 'id', headerName: 'ID', width: 80, ...extra }),
  text: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    flex: 1,
    minWidth: 140,
    ...extra,
  }),
  money: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 130,
    align: 'right',
    headerAlign: 'right',
    valueFormatter: (v: number) => formatCurrency(v),
    ...extra,
  }),
  number: (field: string, headerName: string, digits = 0, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 110,
    align: 'right',
    headerAlign: 'right',
    valueFormatter: (v: number) => formatNumber(v, digits),
    ...extra,
  }),
  percent: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 110,
    align: 'right',
    headerAlign: 'right',
    valueFormatter: (v: number) => formatPercent(v),
    ...extra,
  }),
  date: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 120,
    valueFormatter: (v: string) => formatDate(v),
    ...extra,
  }),
  datetime: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 155,
    valueFormatter: (v: string) => formatDateTime(v),
    ...extra,
  }),
  bool: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 90,
    type: 'boolean',
    ...extra,
  }),
  status: (field: string, headerName: string, extra: Extra = {}): GridColDef => ({
    field,
    headerName,
    width: 140,
    sortable: false,
    renderCell: (p) => <StatusChip status={p.value as string} />,
    ...extra,
  }),
};
