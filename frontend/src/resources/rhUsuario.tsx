import { type ResourceConfig } from '../components/crud/resourceConfig';
import { cols } from './columns';
import { estadoCivilOptions, perfilOptions, sexoOptions, tipoPessoaOptions } from './options';

export const cargosConfig: ResourceConfig = {
  key: 'cargos',
  basePath: '/api/cargos',
  singular: 'Cargo',
  plural: 'Cargos',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.money('salarioBase', 'Salário base'),
    cols.number('cargaHoraria', 'Carga horária'),
    cols.bool('requerCnh', 'CNH'),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'descricao', label: 'Descrição', type: 'text', cols: 6 },
    { name: 'salarioBase', label: 'Salário base', type: 'money', cols: 4 },
    { name: 'cargaHoraria', label: 'Carga horária', type: 'integer', cols: 4 },
    { name: 'requerCnh', label: 'Requer CNH', type: 'switch', cols: 4 },
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 4 },
  ],
};

export const funcionariosConfig: ResourceConfig = {
  key: 'funcionarios',
  basePath: '/api/funcionarios',
  singular: 'Funcionário',
  plural: 'Funcionários',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('cpf', 'CPF', { flex: 0, width: 160 }),
    cols.text('cargoNome', 'Cargo'),
    cols.text('telefone', 'Telefone', { flex: 0, width: 140 }),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'nome', label: 'Nome', type: 'text' },
    {
      name: 'cargoId',
      label: 'Cargo',
      type: 'reference',
      reference: { basePath: '/api/cargos', labelField: 'nome' },
    },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 8 },
    { name: 'tipo', label: 'Tipo', type: 'select', cols: 4, options: tipoPessoaOptions, defaultValue: 'FISICA' },
    { name: 'cpf', label: 'CPF', type: 'text', required: true, cols: 4 },
    { name: 'rgInscricaoEstadual', label: 'RG / Inscr. Estadual', type: 'text', cols: 4 },
    { name: 'apelido', label: 'Apelido', type: 'text', cols: 4 },
    { name: 'dataNascimento', label: 'Nascimento', type: 'date', cols: 4 },
    { name: 'sexo', label: 'Sexo', type: 'select', cols: 4, options: sexoOptions },
    { name: 'estadoCivil', label: 'Estado civil', type: 'select', cols: 4, options: estadoCivilOptions },
    { name: 'telefone', label: 'Telefone', type: 'text', cols: 4 },
    { name: 'email', label: 'E-mail', type: 'text', cols: 8 },
    {
      name: 'cargoId',
      label: 'Cargo',
      type: 'reference',
      required: true,
      cols: 4,
      reference: { basePath: '/api/cargos', labelField: 'nome' },
    },
    { name: 'cnh', label: 'CNH', type: 'text', cols: 4 },
    { name: 'dataValidadeCnh', label: 'Validade CNH', type: 'date', cols: 4 },
    { name: 'dataAdmissao', label: 'Admissão', type: 'date', cols: 4 },
    { name: 'dataDemissao', label: 'Demissão', type: 'date', cols: 4 },
    { name: 'salario', label: 'Salário', type: 'money', cols: 4 },
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
    { name: 'ativo', label: 'Ativo', type: 'switch', cols: 3 },
    { name: 'observacao', label: 'Observação', type: 'textarea' },
  ],
};

export const usuariosConfig: ResourceConfig = {
  key: 'usuarios',
  basePath: '/api/usuarios',
  singular: 'Usuário',
  plural: 'Usuários',
  defaultSort: 'nome,asc',
  columns: [
    cols.id(),
    cols.text('nome', 'Nome'),
    cols.text('login', 'Login'),
    cols.text('email', 'E-mail'),
    cols.text('perfil', 'Perfil', { flex: 0, width: 130 }),
    cols.bool('ativo', 'Ativo'),
  ],
  filters: [
    { name: 'perfil', label: 'Perfil', type: 'select', options: perfilOptions },
    { name: 'ativo', label: 'Situação', type: 'boolean' },
  ],
  fields: [
    { name: 'nome', label: 'Nome', type: 'text', required: true, cols: 6 },
    { name: 'login', label: 'Login', type: 'text', required: true, cols: 3 },
    { name: 'perfil', label: 'Perfil', type: 'select', cols: 3, options: perfilOptions, defaultValue: 'OPERADOR' },
    {
      name: 'senha',
      label: 'Senha',
      type: 'password',
      required: true,
      cols: 6,
      helperText: 'Informe a senha (obrigatória também na edição).',
    },
    { name: 'email', label: 'E-mail', type: 'text', cols: 6 },
    {
      name: 'funcionarioId',
      label: 'Funcionário',
      type: 'reference',
      cols: 6,
      reference: { basePath: '/api/funcionarios', labelField: 'nome' },
    },
  ],
};

export const rhUsuarioConfigs = [cargosConfig, funcionariosConfig, usuariosConfig];
