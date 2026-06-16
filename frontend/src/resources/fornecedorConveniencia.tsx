import { type ResourceConfig } from '../components/crud/resourceConfig';
import { type FieldConfig } from '../components/form/fieldConfig';
import { cols } from './columns';
import { tipoPessoaOptions, tipoEmailOptions, tipoTelefoneOptions } from './options';

const emailSubFields: FieldConfig[] = [
  { name: 'email', label: 'E-mail', type: 'text', required: true, cols: 5 },
  { name: 'tipo', label: 'Tipo', type: 'select', cols: 4, options: tipoEmailOptions },
  { name: 'principal', label: 'Principal', type: 'switch', cols: 3 },
];

const telefoneSubFields: FieldConfig[] = [
  { name: 'telefone', label: 'Telefone', type: 'text', required: true, cols: 5 },
  { name: 'tipo', label: 'Tipo', type: 'select', cols: 4, options: tipoTelefoneOptions },
  { name: 'principal', label: 'Principal', type: 'switch', cols: 3 },
];

export const fornecedoresConfig: ResourceConfig = {
  key: 'fornecedores',
  basePath: '/api/fornecedores',
  singular: 'Fornecedor',
  plural: 'Fornecedores',
  subtitle: 'Fornecedores de produtos e serviços.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('documento', 'Documento', { flex: 0, width: 160 }),
    cols.text('cidadeNome', 'Cidade'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'documento', label: 'Documento', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  toFormValues: (row) => ({
    ...row,
    emails: (row.emails ?? []).map((e: any) => ({
      email: e.email,
      tipo: e.tipo,
      principal: e.principal,
    })),
    telefones: (row.telefones ?? []).map((t: any) => ({
      telefone: t.telefone,
      tipo: t.tipo,
      principal: t.principal,
    })),
  }),
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'nomeFantasia', label: 'Nome fantasia', type: 'text', cols: 6 },
    { name: 'tipo', label: 'Tipo', type: 'select', cols: 4, options: tipoPessoaOptions, defaultValue: 'JURIDICA' },
    { name: 'documento', label: 'CNPJ / CPF', type: 'text', required: true, cols: 4 },
    { name: 'rgInscricaoEstadual', label: 'RG / Inscr. Estadual', type: 'text', cols: 4 },
    { name: 'apelido', label: 'Apelido', type: 'text', cols: 4 },
    { name: 'endereco', label: 'Endereço', type: 'text', cols: 6 },
    { name: 'numero', label: 'Número', type: 'text', cols: 2 },
    { name: 'complemento', label: 'Complemento', type: 'text', cols: 4 },
    { name: 'bairro', label: 'Bairro', type: 'text', cols: 4 },
    { name: 'cep', label: 'CEP', type: 'text', cols: 4 },
    {
      name: 'cidadeId',
      label: 'Cidade',
      type: 'reference',
      cols: 4,
      reference: { basePath: '/api/cidades', labelField: 'nome', secondaryField: 'estadoNome' },
    },
    {
      name: 'nacionalidadeId',
      label: 'Nacionalidade',
      type: 'reference',
      cols: 6,
      reference: { basePath: '/api/paises', labelField: 'nome' },
    },
    {
      name: 'condicaoPagamentoId',
      label: 'Condição de pagamento',
      type: 'reference',
      cols: 6,
      reference: { basePath: '/api/condicoes-pagamento', labelField: 'nome' },
    },
    { name: 'limiteCredito', label: 'Limite de crédito', type: 'money', cols: 4 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 4 },
    { name: 'observacao', label: 'Observação', type: 'textarea' },
    { name: 'emails', label: 'E-mails', type: 'subitems', subFields: emailSubFields },
    { name: 'telefones', label: 'Telefones', type: 'subitems', subFields: telefoneSubFields },
  ],
};

export const categoriasConfig: ResourceConfig = {
  key: 'categorias',
  basePath: '/api/categorias',
  singular: 'Categoria',
  plural: 'Categorias',
  subtitle: 'Categorias de produtos.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('descricao', 'Descrição'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'descricao', label: 'Descrição', type: 'text', cols: 6 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 4 },
  ],
};

export const marcasConfig: ResourceConfig = {
  key: 'marcas',
  basePath: '/api/marcas',
  singular: 'Marca',
  plural: 'Marcas',
  subtitle: 'Marcas de produtos.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 8 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 4 },
  ],
};

export const unidadesMedidaConfig: ResourceConfig = {
  key: 'unidades-medida',
  basePath: '/api/unidades-medida',
  singular: 'Unidade de Medida',
  plural: 'Unidades de Medida',
  subtitle: 'Unidades de medida de produtos.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('sigla', 'Sigla', { flex: 0, width: 110 }),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'sigla', label: 'Sigla', type: 'text', required: true, cols: 3 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 3 },
  ],
};

export const servicosConfig: ResourceConfig = {
  key: 'servicos',
  basePath: '/api/servicos',
  singular: 'Serviço',
  plural: 'Serviços',
  subtitle: 'Serviços prestados.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('descricao', 'Descrição'),
    cols.money('valorPadrao', 'Valor padrão'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'valorPadrao', label: 'Valor padrão', type: 'money', cols: 3 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 3 },
    { name: 'descricao', label: 'Descrição', type: 'textarea' },
  ],
};

export const produtosConfig: ResourceConfig = {
  key: 'produtos',
  basePath: '/api/produtos',
  singular: 'Produto',
  plural: 'Produtos',
  subtitle: 'Produtos de conveniência e estoque.',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('marcaNome', 'Marca'),
    cols.text('categoriaNome', 'Categoria'),
    cols.money('valorVenda', 'Venda'),
    cols.number('quantidade', 'Estoque', 3),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'marcaId', label: 'Marca', type: 'reference', reference: { basePath: '/api/marcas', labelField: 'nome' } },
    {
      name: 'categoriaId',
      label: 'Categoria',
      type: 'reference',
      reference: { basePath: '/api/categorias', labelField: 'nome' },
    },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'codigoBarras', label: 'Cód. barras', type: 'text', cols: 3 },
    { name: 'referencia', label: 'Referência', type: 'text', cols: 3 },
    {
      name: 'marcaId',
      label: 'Marca',
      type: 'reference',
      required: true,
      cols: 4,
      reference: { basePath: '/api/marcas', labelField: 'nome' },
    },
    {
      name: 'unidadeMedidaId',
      label: 'Unidade de medida',
      type: 'reference',
      required: true,
      cols: 4,
      reference: { basePath: '/api/unidades-medida', labelField: 'nome', secondaryField: 'sigla' },
    },
    {
      name: 'categoriaId',
      label: 'Categoria',
      type: 'reference',
      cols: 4,
      reference: { basePath: '/api/categorias', labelField: 'nome' },
    },
    { name: 'valorCompra', label: 'Valor de compra', type: 'money', cols: 3 },
    { name: 'valorVenda', label: 'Valor de venda', type: 'money', cols: 3 },
    { name: 'percentualLucro', label: '% Lucro', type: 'percent', cols: 3 },
    { name: 'quantidade', label: 'Estoque', type: 'number', cols: 3 },
    { name: 'quantidadeMinima', label: 'Estoque mín.', type: 'number', cols: 3 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 3 },
    { name: 'descricao', label: 'Descrição', type: 'text', cols: 6 },
    { name: 'observacao', label: 'Observação', type: 'textarea' },
  ],
};

export const produtoFornecedoresConfig: ResourceConfig = {
  key: 'produto-fornecedores',
  basePath: '/api/produto-fornecedores',
  singular: 'Produto x Fornecedor',
  plural: 'Produtos por Fornecedor',
  subtitle: 'Vínculo de produtos com seus fornecedores.',
  columns: [
    cols.id(),
    cols.text('produtoNome', 'Produto'),
    cols.text('fornecedorNome', 'Fornecedor'),
    cols.text('codigoProd', 'Cód. no fornec.'),
    cols.money('custo', 'Custo'),
    cols.bool('ativo', 'Ativo'),
  ],
  fields: [
    {
      name: 'produtoId',
      label: 'Produto',
      type: 'reference',
      required: true,
      cols: 6,
      reference: { basePath: '/api/produtos', labelField: 'nome' },
    },
    {
      name: 'fornecedorId',
      label: 'Fornecedor',
      type: 'reference',
      required: true,
      cols: 6,
      reference: { basePath: '/api/fornecedores', labelField: 'nome' },
    },
    { name: 'codigoProd', label: 'Código no fornecedor', type: 'text', cols: 6 },
    { name: 'custo', label: 'Custo', type: 'money', cols: 6 },
  ],
};

export const fornecedorConvenienciaConfigs = [
  fornecedoresConfig,
  categoriasConfig,
  marcasConfig,
  unidadesMedidaConfig,
  servicosConfig,
  produtosConfig,
  produtoFornecedoresConfig,
];
