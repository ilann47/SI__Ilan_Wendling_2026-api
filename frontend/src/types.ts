// Tipos do contrato da API Kaneko. Gerado a partir dos DTOs reais do backend.
// Datas (LocalDate/OffsetDateTime) chegam como string ISO.

// ===================== Enums =====================
export type TipoPessoa = 'FISICA' | 'JURIDICA';
export type Sexo = 'MASCULINO' | 'FEMININO' | 'OUTRO';
export type EstadoCivil = 'SOLTEIRO' | 'CASADO' | 'SEPARADO' | 'DIVORCIADO' | 'VIUVO' | 'OUTRO';
export type PerfilUsuario = 'ADMIN' | 'OPERADOR';
export type TipoEmail = 'COMERCIAL' | 'FINANCEIRO' | 'COMPRAS' | 'VENDAS' | 'SUPORTE';
export type TipoTelefone = 'COMERCIAL' | 'RESIDENCIAL' | 'CELULAR' | 'FAX' | 'WHATSAPP';
export type TipoVeiculo = 'CARRO' | 'MOTO';
export type StatusMensalista = 'ATIVO' | 'SUSPENSO' | 'CANCELADO';
export type TipoMovimentacao = 'AVULSO' | 'MENSALISTA';
export type StatusMovimentacao = 'ABERTO' | 'FECHADO' | 'CANCELADO';
export type TipoFrete = 'CIF' | 'FOB' | 'SEM';
export type SituacaoNota = 'PENDENTE' | 'CONFIRMADA' | 'CANCELADA';
export type SituacaoNotaServico = 'PENDENTE' | 'EMITIDA' | 'CANCELADA';
export type SituacaoContaPagar = 'PENDENTE' | 'PARCIAL' | 'PAGA' | 'CANCELADA';
export type SituacaoContaReceber = 'PENDENTE' | 'PARCIAL' | 'RECEBIDA' | 'CANCELADA';

/** Toda resposta de cadastro tem id e timestamps. */
export interface BaseResponse {
  id: number;
  ativo?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// ===================== Auth =====================
export interface LoginRequest {
  login: string;
  senha: string;
}
export interface LoginResponse {
  token: string;
  tipo: string;
  login: string;
  perfil: PerfilUsuario;
}

// ===================== Geografia =====================
export interface PaisResponse {
  id: number;
  nome: string;
  sigla?: string;
  codigo?: string;
  nacionalidade?: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface PaisRequest {
  nome: string;
  sigla?: string;
  codigo?: string;
  nacionalidade?: string;
  ativo?: boolean;
}

export interface EstadoResponse {
  id: number;
  nome: string;
  uf: string;
  paisId: number;
  paisNome: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface EstadoRequest {
  nome: string;
  uf: string;
  paisId: number;
  ativo?: boolean;
}

export interface CidadeResponse {
  id: number;
  nome: string;
  ddd?: string;
  codigoIbge?: string;
  estadoId: number;
  estadoNome: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface CidadeRequest {
  nome: string;
  ddd?: string;
  codigoIbge?: string;
  estadoId: number;
  ativo?: boolean;
}

// ===================== Pessoas / Pagamento =====================
export interface ClienteResponse {
  id: number;
  nome: string;
  apelido?: string;
  tipo?: TipoPessoa;
  documento: string;
  rgInscricaoEstadual?: string;
  dataNascimento?: string;
  sexo?: Sexo;
  estadoCivil?: EstadoCivil;
  telefone?: string;
  email?: string;
  endereco?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cep?: string;
  cidadeId?: number;
  cidadeNome?: string;
  nacionalidadeId?: number;
  nacionalidadeNome?: string;
  condicaoPagamentoId?: number;
  condicaoPagamentoNome?: string;
  limiteCredito?: number;
  observacao?: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface ClienteRequest {
  nome: string;
  apelido?: string;
  tipo?: TipoPessoa;
  documento: string;
  rgInscricaoEstadual?: string;
  dataNascimento?: string;
  sexo?: Sexo;
  estadoCivil?: EstadoCivil;
  telefone?: string;
  email?: string;
  endereco?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cep?: string;
  cidadeId?: number;
  nacionalidadeId?: number;
  condicaoPagamentoId?: number;
  limiteCredito?: number;
  observacao?: string;
  ativo?: boolean;
}

export interface FormaPagamentoResponse {
  id: number;
  nome: string;
  descricao: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface FormaPagamentoRequest {
  nome: string;
  descricao: string;
  ativo?: boolean;
}

export interface ParcelaResponse {
  id: number;
  numero: number;
  dias: number;
  percentual: number;
  formaPagamentoId?: number;
  formaPagamentoNome?: string;
}
export interface ParcelaRequest {
  numero: number;
  dias: number;
  percentual: number;
  formaPagamentoId?: number;
}
export interface CondicaoPagamentoResponse {
  id: number;
  nome: string;
  numeroParcelas: number;
  diasPrimeiraParcela: number;
  diasEntreParcelas: number;
  percentualJuros: number;
  percentualMulta: number;
  percentualDesconto: number;
  ativo?: boolean;
  parcelas: ParcelaResponse[];
  createdAt: string;
  updatedAt: string;
}
export interface CondicaoPagamentoRequest {
  nome: string;
  numeroParcelas: number;
  diasPrimeiraParcela?: number;
  diasEntreParcelas?: number;
  percentualJuros?: number;
  percentualMulta?: number;
  percentualDesconto?: number;
  ativo?: boolean;
  parcelas?: ParcelaRequest[];
}

// ===================== RH / Usuario =====================
export interface CargoResponse {
  id: number;
  nome: string;
  descricao?: string;
  salarioBase?: number;
  cargaHoraria?: number;
  requerCnh?: boolean;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface CargoRequest {
  nome: string;
  descricao?: string;
  salarioBase?: number;
  cargaHoraria?: number;
  requerCnh?: boolean;
  ativo?: boolean;
}

export interface FuncionarioResponse {
  id: number;
  nome: string;
  apelido?: string;
  tipo: TipoPessoa;
  cpf: string;
  rgInscricaoEstadual?: string;
  dataNascimento?: string;
  sexo?: Sexo;
  estadoCivil?: EstadoCivil;
  telefone?: string;
  email?: string;
  endereco?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cep?: string;
  cidadeId?: number;
  cidadeNome?: string;
  nacionalidadeId?: number;
  nacionalidadeNome?: string;
  cargoId: number;
  cargoNome: string;
  cnh?: string;
  dataValidadeCnh?: string;
  dataAdmissao?: string;
  dataDemissao?: string;
  salario?: number;
  observacao?: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface FuncionarioRequest {
  nome: string;
  apelido?: string;
  tipo?: TipoPessoa;
  cpf: string;
  rgInscricaoEstadual?: string;
  dataNascimento?: string;
  sexo?: Sexo;
  estadoCivil?: EstadoCivil;
  telefone?: string;
  email?: string;
  endereco?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cep?: string;
  cidadeId?: number;
  nacionalidadeId?: number;
  cargoId: number;
  cnh?: string;
  dataValidadeCnh?: string;
  dataAdmissao?: string;
  dataDemissao?: string;
  salario?: number;
  observacao?: string;
  ativo?: boolean;
}

export interface UsuarioResponse {
  id: number;
  nome: string;
  login: string;
  email?: string;
  funcionarioId?: number;
  perfil?: PerfilUsuario;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface UsuarioRequest {
  nome: string;
  login: string;
  senha: string;
  email?: string;
  funcionarioId?: number;
  perfil?: PerfilUsuario;
}

// ===================== Fornecedor =====================
export interface EmailResponse {
  id: number;
  email: string;
  tipo?: TipoEmail;
  principal?: boolean;
  ativo?: boolean;
}
export interface EmailRequest {
  email: string;
  tipo?: TipoEmail;
  principal?: boolean;
}
export interface TelefoneResponse {
  id: number;
  telefone: string;
  tipo?: TipoTelefone;
  principal?: boolean;
  ativo?: boolean;
}
export interface TelefoneRequest {
  telefone: string;
  tipo?: TipoTelefone;
  principal?: boolean;
}
export interface FornecedorResponse {
  id: number;
  nome: string;
  nomeFantasia?: string;
  apelido?: string;
  tipo?: TipoPessoa;
  documento: string;
  rgInscricaoEstadual?: string;
  endereco?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cep?: string;
  cidadeId?: number;
  cidadeNome?: string;
  nacionalidadeId?: number;
  nacionalidadeNome?: string;
  condicaoPagamentoId?: number;
  condicaoPagamentoNome?: string;
  limiteCredito?: number;
  observacao?: string;
  ativo?: boolean;
  emails: EmailResponse[];
  telefones: TelefoneResponse[];
  createdAt?: string;
  updatedAt?: string;
}
export interface FornecedorRequest {
  nome: string;
  nomeFantasia?: string;
  apelido?: string;
  tipo?: TipoPessoa;
  documento: string;
  rgInscricaoEstadual?: string;
  endereco?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cep?: string;
  cidadeId?: number;
  nacionalidadeId?: number;
  condicaoPagamentoId?: number;
  limiteCredito?: number;
  observacao?: string;
  ativo?: boolean;
  emails?: EmailRequest[];
  telefones?: TelefoneRequest[];
}

// ===================== Conveniencia =====================
export interface CategoriaResponse {
  id: number;
  nome: string;
  descricao?: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface CategoriaRequest {
  nome: string;
  descricao?: string;
  ativo?: boolean;
}

export interface MarcaResponse {
  id: number;
  nome: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface MarcaRequest {
  nome: string;
  ativo?: boolean;
}

export interface UnidadeMedidaResponse {
  id: number;
  nome: string;
  sigla: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface UnidadeMedidaRequest {
  nome: string;
  sigla: string;
  ativo?: boolean;
}

export interface ServicoResponse {
  id: number;
  nome: string;
  descricao?: string;
  valorPadrao?: number;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface ServicoRequest {
  nome: string;
  descricao?: string;
  valorPadrao?: number;
  ativo?: boolean;
}

export interface ProdutoResponse {
  id: number;
  nome: string;
  codigoBarras?: string;
  referencia?: string;
  marcaId: number;
  marcaNome: string;
  unidadeMedidaId: number;
  unidadeMedidaSigla: string;
  categoriaId?: number;
  categoriaNome?: string;
  valorCompra?: number;
  valorVenda?: number;
  percentualLucro?: number;
  quantidade?: number;
  quantidadeMinima?: number;
  descricao?: string;
  observacao?: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface ProdutoRequest {
  nome: string;
  codigoBarras?: string;
  referencia?: string;
  marcaId: number;
  unidadeMedidaId: number;
  categoriaId?: number;
  valorCompra?: number;
  valorVenda?: number;
  percentualLucro?: number;
  quantidade?: number;
  quantidadeMinima?: number;
  descricao?: string;
  observacao?: string;
  ativo?: boolean;
}

export interface ProdutoFornecedorResponse {
  id: number;
  produtoId: number;
  produtoNome: string;
  fornecedorId: number;
  fornecedorNome: string;
  codigoProd?: string;
  custo?: number;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface ProdutoFornecedorRequest {
  produtoId: number;
  fornecedorId: number;
  codigoProd?: string;
  custo?: number;
}

// ===================== Patio =====================
export interface TarifaResponse {
  id: number;
  descricao: string;
  valorHora: number;
  valorDiaria: number;
  toleranciaMin?: number;
  valorMensalidade: number;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface TarifaRequest {
  descricao: string;
  valorHora: number;
  valorDiaria: number;
  toleranciaMin?: number;
  valorMensalidade: number;
  ativo?: boolean;
}

export interface VeiculoResponse {
  id: number;
  placa: string;
  modelo?: string;
  cor?: string;
  tipo?: TipoVeiculo;
  clienteId?: number;
  clienteNome?: string;
  observacao?: string;
  ativo?: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface VeiculoRequest {
  placa: string;
  modelo?: string;
  cor?: string;
  tipo?: TipoVeiculo;
  clienteId?: number;
  observacao?: string;
  ativo?: boolean;
}

export interface MensalistaResponse {
  id: number;
  clienteId: number;
  clienteNome: string;
  veiculoId: number;
  veiculoPlaca: string;
  valorMensal: number;
  dataInicio?: string;
  dataFim?: string;
  status?: StatusMensalista;
  createdAt: string;
  updatedAt: string;
}
export interface MensalistaRequest {
  clienteId: number;
  veiculoId: number;
  valorMensal: number;
  dataInicio?: string;
  dataFim?: string;
  status?: StatusMensalista;
}

export interface MovimentacaoResponse {
  id: number;
  veiculoId: number;
  veiculoPlaca: string;
  tipo?: TipoMovimentacao;
  mensalistaId?: number;
  tarifaId?: number;
  dataEntrada: string;
  dataSaida?: string;
  valorCobrado?: number;
  status?: StatusMovimentacao;
  createdAt: string;
  updatedAt: string;
}
export interface EntradaRequest {
  veiculoId: number;
}

// ===================== Fiscal =====================
export interface ItemNotaEntradaResponse {
  id: number;
  produtoId: number;
  produtoNome: string;
  sequencia: number;
  quantidade: number;
  valorUnitario: number;
  valorDesconto: number;
  percentualDesconto: number;
  valorTotal: number;
  rateioFrete: number;
  rateioSeguro: number;
  rateioOutras: number;
  custoPrecoFinal: number;
}
export interface NotaEntradaResponse {
  id: number;
  numero: string;
  modelo: string;
  serie: string;
  fornecedorId: number;
  fornecedorNome: string;
  condicaoPagamentoId?: number;
  condicaoPagamentoNome?: string;
  dataEmissao: string;
  dataChegada?: string;
  tipoFrete: TipoFrete;
  valorProdutos: number;
  valorFrete: number;
  valorSeguro: number;
  outrasDespesas: number;
  valorDesconto: number;
  valorTotal: number;
  situacao: SituacaoNota;
  observacao?: string;
  itens: ItemNotaEntradaResponse[];
  createdAt: string;
  updatedAt: string;
}
export interface ItemNotaEntradaRequest {
  produtoId: number;
  quantidade: number;
  valorUnitario: number;
  valorDesconto?: number;
  percentualDesconto?: number;
}
export interface NotaEntradaRequest {
  numero: string;
  modelo?: string;
  serie?: string;
  fornecedorId: number;
  condicaoPagamentoId?: number;
  dataEmissao?: string;
  dataChegada?: string;
  tipoFrete?: TipoFrete;
  valorFrete?: number;
  valorSeguro?: number;
  outrasDespesas?: number;
  valorDesconto?: number;
  observacao?: string;
  itens: ItemNotaEntradaRequest[];
}

export interface ItemNotaSaidaResponse {
  id: number;
  produtoId: number;
  produtoNome: string;
  sequencia: number;
  quantidade: number;
  valorUnitario: number;
  valorDesconto: number;
  percentualDesconto: number;
  valorTotal: number;
  rateioFrete: number;
  rateioSeguro: number;
  rateioOutras: number;
  custoPrecoFinal: number;
}
export interface NotaSaidaResponse {
  id: number;
  numero: string;
  modelo: string;
  serie: string;
  clienteId: number;
  clienteNome: string;
  condicaoPagamentoId?: number;
  condicaoPagamentoNome?: string;
  dataEmissao: string;
  dataSaida?: string;
  tipoFrete: TipoFrete;
  valorProdutos: number;
  valorFrete: number;
  valorSeguro: number;
  outrasDespesas: number;
  valorDesconto: number;
  valorTotal: number;
  situacao: SituacaoNota;
  observacao?: string;
  itens: ItemNotaSaidaResponse[];
  createdAt: string;
  updatedAt: string;
}
export interface ItemNotaSaidaRequest {
  produtoId: number;
  quantidade: number;
  valorUnitario: number;
  valorDesconto?: number;
  percentualDesconto?: number;
}
export interface NotaSaidaRequest {
  numero: string;
  modelo?: string;
  serie?: string;
  clienteId: number;
  condicaoPagamentoId?: number;
  dataEmissao?: string;
  dataSaida?: string;
  tipoFrete?: TipoFrete;
  valorFrete?: number;
  valorSeguro?: number;
  outrasDespesas?: number;
  valorDesconto?: number;
  observacao?: string;
  itens: ItemNotaSaidaRequest[];
}

export interface NotaServicoResponse {
  id: number;
  numero: string;
  modelo: string;
  serie: string;
  clienteId: number;
  clienteNome: string;
  servicoId?: number;
  movimentacaoId?: number;
  mensalistaId?: number;
  condicaoPagamentoId?: number;
  formaPagamentoId?: number;
  dataEmissao: string;
  valorServico: number;
  aliquotaIss: number;
  valorIss: number;
  valorDesconto: number;
  valorTotal: number;
  situacao: SituacaoNotaServico;
  observacao?: string;
  createdAt: string;
  updatedAt: string;
}
export interface NotaServicoRequest {
  numero: string;
  modelo?: string;
  serie?: string;
  clienteId: number;
  servicoId?: number;
  movimentacaoId?: number;
  mensalistaId?: number;
  condicaoPagamentoId?: number;
  formaPagamentoId?: number;
  dataEmissao?: string;
  valorServico: number;
  aliquotaIss?: number;
  valorDesconto?: number;
  observacao?: string;
}

// ===================== Financeiro =====================
export interface BaixaRequest {
  valor: number;
  data?: string;
}

export interface ContaPagarResponse {
  id: number;
  fornecedorId: number;
  fornecedorNome: string;
  notaEntradaId?: number;
  notaEntradaNumero?: string;
  condicaoPagamentoId?: number;
  formaPagamentoId?: number;
  formaPagamentoNome?: string;
  numeroParcela?: number;
  totalParcelas?: number;
  valorOriginal: number;
  valorPago: number;
  valorDesconto: number;
  valorJuros: number;
  valorMulta: number;
  valorTotal: number;
  dataEmissao?: string;
  dataVencimento: string;
  dataPagamento?: string;
  situacao: SituacaoContaPagar;
  observacao?: string;
  createdAt: string;
  updatedAt: string;
}
export interface ContaPagarRequest {
  fornecedorId: number;
  condicaoPagamentoId?: number;
  formaPagamentoId?: number;
  numeroParcela?: number;
  totalParcelas?: number;
  valorOriginal: number;
  valorDesconto?: number;
  valorJuros?: number;
  valorMulta?: number;
  valorTotal?: number;
  dataEmissao?: string;
  dataVencimento: string;
  observacao?: string;
}

export interface ContaReceberResponse {
  id: number;
  clienteId: number;
  clienteNome: string;
  notaSaidaId?: number;
  notaSaidaNumero?: string;
  mensalistaId?: number;
  condicaoPagamentoId?: number;
  formaPagamentoId?: number;
  formaPagamentoNome?: string;
  numeroParcela?: number;
  totalParcelas?: number;
  valorOriginal: number;
  valorRecebido: number;
  valorDesconto: number;
  valorJuros: number;
  valorMulta: number;
  valorTotal: number;
  dataEmissao?: string;
  dataVencimento: string;
  dataRecebimento?: string;
  situacao: SituacaoContaReceber;
  observacao?: string;
  createdAt: string;
  updatedAt: string;
}
export interface ContaReceberRequest {
  clienteId: number;
  condicaoPagamentoId?: number;
  formaPagamentoId?: number;
  numeroParcela?: number;
  totalParcelas?: number;
  valorOriginal: number;
  valorDesconto?: number;
  valorJuros?: number;
  valorMulta?: number;
  valorTotal?: number;
  dataEmissao?: string;
  dataVencimento: string;
  observacao?: string;
}

export interface ContaPagarAvulsaResponse {
  id: number;
  descricao: string;
  fornecedorId?: number;
  fornecedorNome?: string;
  formaPagamentoId?: number;
  formaPagamentoNome?: string;
  numeroNota?: string;
  modelo?: string;
  serie?: string;
  numeroParcela?: number;
  totalParcelas?: number;
  valorOriginal: number;
  valorPago: number;
  valorDesconto: number;
  valorJuros: number;
  valorMulta: number;
  valorTotal: number;
  dataEmissao?: string;
  dataVencimento: string;
  dataPagamento?: string;
  situacao: SituacaoContaPagar;
  observacao?: string;
  createdAt: string;
  updatedAt: string;
}
export interface ContaPagarAvulsaRequest {
  descricao: string;
  fornecedorId?: number;
  formaPagamentoId?: number;
  numeroNota?: string;
  modelo?: string;
  serie?: string;
  numeroParcela?: number;
  totalParcelas?: number;
  valorOriginal: number;
  valorDesconto?: number;
  valorJuros?: number;
  valorMulta?: number;
  valorTotal?: number;
  dataEmissao?: string;
  dataVencimento: string;
  observacao?: string;
}

// ===================== Relatorios =====================
export interface ItemPatio {
  movimentacaoId: number;
  placa: string;
  modelo: string;
  tipo: string;
  dataEntrada: string;
  minutosPermanencia: number;
}
export interface ResumoPatio {
  totalVeiculos: number;
  avulsos: number;
  mensalistas: number;
}
export interface PatioAtualResponse {
  itens: ItemPatio[];
  resumo: ResumoPatio;
}

export interface FaturamentoDiaResponse {
  inicio: string;
  fim: string;
  estacionamento: number;
  vendas: number;
  servicos: number;
  totalFaturado: number;
  recebimentos: number;
}

export interface Titulo {
  origem: string;
  id: number;
  contraparte: string;
  vencimento: string;
  saldo: number;
  vencido: boolean;
  situacao: string;
}
export interface ContasAVencerResponse {
  inicio: string;
  fim: string;
  aPagar: Titulo[];
  aReceber: Titulo[];
  totalAPagar: number;
  totalAReceber: number;
  vencidoAPagar: number;
  vencidoAReceber: number;
}

export interface ItemEstoque {
  produtoId: number;
  produto: string;
  marca: string;
  categoria: string;
  quantidade: number;
  quantidadeMinima: number;
}
export interface EstoqueMinimoResponse {
  total: number;
  itens: ItemEstoque[];
}

export interface ItemMensalista {
  mensalistaId: number;
  cliente: string;
  placa: string;
  modelo: string;
  valorMensal: number;
  dataInicio: string;
}
export interface MensalistasAtivosResponse {
  total: number;
  receitaMensalRecorrente: number;
  itens: ItemMensalista[];
}
