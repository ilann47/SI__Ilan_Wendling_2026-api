import { type ResourceConfig } from '../components/crud/resourceConfig';
import { geografiaConfigs } from './geografia';
import { pessoasPagamentoConfigs } from './pessoasPagamento';
import { rhUsuarioConfigs } from './rhUsuario';
import { fornecedorConvenienciaConfigs } from './fornecedorConveniencia';
import { patioConfigs } from './patio';
import { fiscalConfigs } from './fiscal';
import { financeiroConfigs } from './financeiro';

export const allConfigs: ResourceConfig[] = [
  ...geografiaConfigs,
  ...pessoasPagamentoConfigs,
  ...rhUsuarioConfigs,
  ...fornecedorConvenienciaConfigs,
  ...patioConfigs,
  ...fiscalConfigs,
  ...financeiroConfigs,
];

export const configByKey: Record<string, ResourceConfig> = Object.fromEntries(
  allConfigs.map((c) => [c.key, c]),
);
