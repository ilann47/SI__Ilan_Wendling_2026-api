# Estacionamento Kaneko — Frontend

SPA moderna (TypeScript + React + MUI) que consome a API do backend Spring Boot.

## Stack

- **Vite** + **React 18** + **TypeScript**
- **MUI v6** (Material UI) + **MUI X DataGrid** / **Date Pickers**
- **React Router** (rotas) · **TanStack React Query** (estado de servidor)
- **React Hook Form** (formulários) · **Axios** (HTTP, com JWT)

## Pré-requisitos

- **Node 18+** (testado com Node 24)
- O **backend** rodando em `http://localhost:8086` (veja o README da raiz do projeto).

## Como executar

```bash
cd frontend
npm install
npm run dev
```

Abra **http://localhost:5173**. O dev server faz **proxy** de `/api` e `/actuator`
para o backend (`http://localhost:8086`) — sem CORS. Para apontar para outro host:

```bash
# Windows PowerShell
$env:VITE_BACKEND_URL = "http://meu-host:8086"; npm run dev
```

## Primeiro acesso

Na primeira subida, o backend cria um administrador inicial (quando não há
nenhum usuário):

- **Login:** `admin`
- **Senha:** `admin123`

> Troque a senha após o primeiro acesso (menu Usuários). As credenciais podem ser
> configuradas no backend por `APP_ADMIN_LOGIN` / `APP_ADMIN_SENHA`.

## Build de produção

```bash
npm run build      # tsc + vite build  ->  dist/
npm run preview    # serve o build (porta 4173, com proxy /api)
```

## O que tem

- **Login** com JWT (token guardado no navegador; expiração tratada).
- **Dashboard** com KPIs (pátio atual, faturamento do dia, receita recorrente, estoque baixo).
- **Pátio**: registrar entrada/saída e ver os veículos no pátio em tempo real.
- **CRUD completo** de todos os cadastros (geografia, pessoas, RH, conveniência,
  fornecedores, pagamento), com **filtros**, paginação no servidor e validação.
- **Fiscal**: notas de entrada/saída (com itens) e de serviço — confirmar, cancelar, emitir.
- **Financeiro**: contas a pagar/receber/avulsas com **baixa** e cancelamento.
- **Relatórios**: faturamento, contas a vencer, estoque mínimo, mensalistas ativos.
- Tema claro/escuro, layout responsivo (drawer), notificações e tratamento de erros (ProblemDetail).

## Arquitetura

A interface é orientada por um **registry declarativo** (`src/resources/`): cada
recurso é descrito por colunas, campos de formulário, filtros e ações. Um motor
genérico (`src/components/crud/` + `src/components/form/`) renderiza a listagem
(DataGrid), o formulário (incl. sub-itens como parcelas/itens de nota) e as ações
customizadas (baixa, confirmação, etc.). Páginas sob medida: Login, Dashboard,
Pátio e Relatórios.

```
src/
  api/          cliente axios (JWT) + helpers de recurso (CRUD paginado)
  auth/         contexto de autenticação + rota protegida
  components/   form/ (campos), crud/ (DataGrid + ações), common/ (chips, diálogos)
  context/      modo claro/escuro
  layout/       AppLayout (drawer + appbar) + navegação
  pages/        Login, Dashboard, Pátio, Relatórios
  resources/    registry declarativo dos recursos (1 arquivo por domínio)
  types.ts      tipos do contrato da API (gerados a partir dos DTOs reais)
```
