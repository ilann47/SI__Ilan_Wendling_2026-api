import { type ResourceConfig } from '../components/crud/resourceConfig';
import { cols } from './columns';

export const paisesConfig: ResourceConfig = {
  key: 'paises',
  basePath: '/api/paises',
  singular: 'País',
  plural: 'Países',
  subtitle: 'Países e nacionalidades.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('sigla', 'Sigla', { flex: 0, width: 100 }),
    cols.text('codigo', 'Código', { flex: 0, width: 100 }),
    cols.text('nacionalidade', 'Nacionalidade'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'sigla', label: 'Sigla', type: 'text', cols: 3 },
    { name: 'codigo', label: 'Código', type: 'text', cols: 3 },
    { name: 'nacionalidade', label: 'Nacionalidade (gentílico)', type: 'text', cols: 8 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 4 },
  ],
};

export const estadosConfig: ResourceConfig = {
  key: 'estados',
  basePath: '/api/estados',
  singular: 'Estado',
  plural: 'Estados',
  subtitle: 'Unidades federativas por país.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('uf', 'UF', { flex: 0, width: 90 }),
    cols.text('paisNome', 'País'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'paisId', label: 'País', type: 'reference', reference: { basePath: '/api/paises', labelField: 'nome' } },
    { name: 'uf', label: 'UF', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 8 },
    { name: 'uf', label: 'UF', type: 'text', required: true, cols: 4 },
    {
      name: 'paisId',
      label: 'País',
      type: 'reference',
      required: true,
      cols: 8,
      reference: { basePath: '/api/paises', labelField: 'nome' },
    },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 4 },
  ],
};

export const cidadesConfig: ResourceConfig = {
  key: 'cidades',
  basePath: '/api/cidades',
  singular: 'Cidade',
  plural: 'Cidades',
  subtitle: 'Municípios por estado.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('estadoNome', 'Estado'),
    cols.text('ddd', 'DDD', { flex: 0, width: 90 }),
    cols.text('codigoIbge', 'IBGE', { flex: 0, width: 110 }),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    {
      name: 'estadoId',
      label: 'Estado',
      type: 'reference',
      reference: { basePath: '/api/estados', labelField: 'nome', secondaryField: 'uf' },
    },
    { name: 'nome', label: 'Nome', type: 'text' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    {
      name: 'estadoId',
      label: 'Estado',
      type: 'reference',
      required: true,
      cols: 6,
      reference: { basePath: '/api/estados', labelField: 'nome', secondaryField: 'uf' },
    },
    { name: 'ddd', label: 'DDD', type: 'text', cols: 3 },
    { name: 'codigoIbge', label: 'Código IBGE', type: 'text', cols: 3 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 6 },
  ],
};

export const geografiaConfigs = [paisesConfig, estadosConfig, cidadesConfig];
