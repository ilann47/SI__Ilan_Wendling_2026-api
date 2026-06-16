import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import LogoutIcon from '@mui/icons-material/Logout';
import { type ResourceConfig } from '../components/crud/resourceConfig';
import { cols } from './columns';
import { tipoVeiculoOptions, statusMensalistaOptions } from './options';

export const tarifasConfig: ResourceConfig = {
  key: 'tarifas',
  basePath: '/api/tarifas',
  singular: 'Tarifa',
  plural: 'Tarifas',
  defaultSort: 'descricao,asc',
  columns: [
    cols.id(),
    cols.text('descricao', 'Descrição'),
    cols.money('valorHora', 'Valor/hora'),
    cols.money('valorDiaria', 'Diária'),
    cols.money('valorMensalidade', 'Mensalidade'),
    cols.number('toleranciaMin', 'Tolerância (min)'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'descricao', label: 'Descrição', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'descricao', label: 'Descrição', type: 'text', required: true, cols: 6 },
    { name: 'valorHora', label: 'Valor/hora', type: 'money', required: true, cols: 3 },
    { name: 'valorDiaria', label: 'Diária', type: 'money', required: true, cols: 3 },
    { name: 'valorMensalidade', label: 'Mensalidade', type: 'money', required: true, cols: 3 },
    { name: 'toleranciaMin', label: 'Tolerância (min)', type: 'integer', cols: 3 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 3 },
  ],
};

export const veiculosConfig: ResourceConfig = {
  key: 'veiculos',
  basePath: '/api/veiculos',
  singular: 'Veículo',
  plural: 'Veículos',
  defaultSort: 'placa,asc',
  columns: [
    cols.id(),
    cols.text('placa', 'Placa', { flex: 0, width: 120 }),
    cols.text('modelo', 'Modelo'),
    cols.text('cor', 'Cor', { flex: 0, width: 110 }),
    cols.text('tipo', 'Tipo', { flex: 0, width: 100 }),
    cols.text('clienteNome', 'Cliente'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'placa', label: 'Placa', type: 'text' },
    { name: 'clienteId', label: 'Cliente', type: 'reference', reference: { basePath: '/api/clientes', labelField: 'nome' } },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'placa', label: 'Placa', type: 'text', required: true, cols: 3 },
    { name: 'tipo', label: 'Tipo', type: 'select', cols: 3, options: tipoVeiculoOptions, defaultValue: 'CARRO' },
    { name: 'modelo', label: 'Modelo', type: 'text', cols: 3 },
    { name: 'cor', label: 'Cor', type: 'text', cols: 3 },
    {
      name: 'clienteId',
      label: 'Cliente (mensalista)',
      type: 'reference',
      cols: 6,
      reference: { basePath: '/api/clientes', labelField: 'nome' },
    },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 3 },
    { name: 'observacao', label: 'Observação', type: 'textarea' },
  ],
};

export const mensalistasConfig: ResourceConfig = {
  key: 'mensalistas',
  basePath: '/api/mensalistas',
  singular: 'Mensalista',
  plural: 'Mensalistas',
  columns: [
    cols.id(),
    cols.text('clienteNome', 'Cliente'),
    cols.text('veiculoPlaca', 'Veículo', { flex: 0, width: 130 }),
    cols.money('valorMensal', 'Mensalidade'),
    cols.date('dataInicio', 'Início'),
    cols.status('status', 'Status'),
  ],
  rowActions: [
    {
      key: 'mensalidade',
      label: 'Gerar mensalidade',
      icon: <ReceiptLongIcon />,
      method: 'post',
      pathSuffix: (row) => `/${row.id}/mensalidade`,
      payloadAs: 'params',
      formFields: [{ name: 'vencimento', label: 'Vencimento', type: 'date' }],
      visible: (row) => row.status === 'ATIVO',
    },
  ],
  fields: [
    {
      name: 'clienteId',
      label: 'Cliente',
      type: 'reference',
      required: true,
      cols: 6,
      reference: { basePath: '/api/clientes', labelField: 'nome' },
    },
    {
      name: 'veiculoId',
      label: 'Veículo',
      type: 'reference',
      required: true,
      cols: 6,
      reference: { basePath: '/api/veiculos', labelField: 'placa', secondaryField: 'modelo' },
    },
    { name: 'valorMensal', label: 'Mensalidade', type: 'money', required: true, cols: 4 },
    { name: 'dataInicio', label: 'Início', type: 'date', cols: 4 },
    { name: 'dataFim', label: 'Fim', type: 'date', cols: 4 },
    { name: 'status', label: 'Status', type: 'select', cols: 4, options: statusMensalistaOptions, defaultValue: 'ATIVO' },
  ],
};

export const movimentacoesConfig: ResourceConfig = {
  key: 'movimentacoes',
  basePath: '/api/movimentacoes',
  singular: 'Movimentação',
  plural: 'Movimentações',
  canCreate: false,
  canEdit: false,
  canDelete: false,
  columns: [
    cols.id(),
    cols.text('veiculoPlaca', 'Veículo', { flex: 0, width: 130 }),
    cols.text('tipo', 'Tipo', { flex: 0, width: 120 }),
    cols.datetime('dataEntrada', 'Entrada'),
    cols.datetime('dataSaida', 'Saída'),
    cols.money('valorCobrado', 'Cobrado'),
    cols.status('status', 'Status'),
  ],
  rowActions: [
    {
      key: 'saida',
      label: 'Registrar saída',
      icon: <LogoutIcon />,
      method: 'post',
      pathSuffix: (row) => `/${row.id}/saida`,
      confirm: 'Registrar a saída deste veículo e calcular a cobrança?',
      visible: (row) => row.status === 'ABERTO',
    },
  ],
  fields: [],
};

export const patioConfigs = [tarifasConfig, veiculosConfig, mensalistasConfig, movimentacoesConfig];
