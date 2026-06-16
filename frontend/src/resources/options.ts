import { type SelectOption } from '../components/form/fieldConfig';

export const tipoPessoaOptions: SelectOption[] = [
  { value: 'FISICA', label: 'Física' },
  { value: 'JURIDICA', label: 'Jurídica' },
];

export const sexoOptions: SelectOption[] = [
  { value: 'MASCULINO', label: 'Masculino' },
  { value: 'FEMININO', label: 'Feminino' },
  { value: 'OUTRO', label: 'Outro' },
];

export const estadoCivilOptions: SelectOption[] = [
  { value: 'SOLTEIRO', label: 'Solteiro(a)' },
  { value: 'CASADO', label: 'Casado(a)' },
  { value: 'SEPARADO', label: 'Separado(a)' },
  { value: 'DIVORCIADO', label: 'Divorciado(a)' },
  { value: 'VIUVO', label: 'Viúvo(a)' },
  { value: 'OUTRO', label: 'Outro' },
];

export const perfilOptions: SelectOption[] = [
  { value: 'ADMIN', label: 'Administrador' },
  { value: 'OPERADOR', label: 'Operador' },
];

export const tipoVeiculoOptions: SelectOption[] = [
  { value: 'CARRO', label: 'Carro' },
  { value: 'MOTO', label: 'Moto' },
];

export const statusMensalistaOptions: SelectOption[] = [
  { value: 'ATIVO', label: 'Ativo' },
  { value: 'SUSPENSO', label: 'Suspenso' },
  { value: 'CANCELADO', label: 'Cancelado' },
];

export const tipoFreteOptions: SelectOption[] = [
  { value: 'CIF', label: 'CIF (remetente)' },
  { value: 'FOB', label: 'FOB (destinatário)' },
  { value: 'SEM', label: 'Sem frete' },
];

export const tipoEmailOptions: SelectOption[] = [
  { value: 'COMERCIAL', label: 'Comercial' },
  { value: 'FINANCEIRO', label: 'Financeiro' },
  { value: 'COMPRAS', label: 'Compras' },
  { value: 'VENDAS', label: 'Vendas' },
  { value: 'SUPORTE', label: 'Suporte' },
];

export const tipoTelefoneOptions: SelectOption[] = [
  { value: 'COMERCIAL', label: 'Comercial' },
  { value: 'RESIDENCIAL', label: 'Residencial' },
  { value: 'CELULAR', label: 'Celular' },
  { value: 'FAX', label: 'Fax' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
];
