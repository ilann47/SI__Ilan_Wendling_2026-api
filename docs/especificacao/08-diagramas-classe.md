> Links: [[README]] · [[03-der]] · [[07-dicionario-de-dados]]

# 8. Diagramas de Classe (UML)

Esta seção apresenta o modelo de classes do sistema **Estacionamento Kaneko**, derivado diretamente do esquema relacional (`database/schema.sql`, 31 tabelas), que originará as entidades JPA da camada de domínio. Cada tabela é representada por uma classe em notação UML, com atributos tipados no estilo Java/JPA (`Long`, `String`, `BigDecimal`, `Integer`, `LocalDate`, `LocalDateTime`, `Boolean`) e enumerações modeladas como classes `<<enumeration>>`.

As associações refletem as chaves estrangeiras do banco, com rótulos de cardinalidade. Adotam-se as convenções:

- **Associação** (`-->`): relação por chave estrangeira navegável (ex.: `Estado "*" --> "1" Pais`).
- **Composição** (`*--`): o filho não existe sem o pai e é removido em cascata com ele — usado nos itens de nota fiscal (`ON DELETE CASCADE` no schema).
- **Dependência** (`..>`): vínculo opcional/fraco ou referência cruzada de origem.

Para legibilidade, os mapeamentos `LocalDateTime criadoEm` / `atualizadoEm` (colunas `created_at` / `updated_at`, presentes em quase todas as tabelas) e os identificadores `Long id` são exibidos nas classes, mas as auditorias temporais são omitidas nas classes onde poluiriam o diagrama sem agregar — permanecem implícitas via superclasse técnica de persistência. Os diagramas estão divididos por módulo funcional.

---

## 8.1 Módulo Geografia

Hierarquia de localização (País → Estado → Cidade), referenciada por Funcionário, Fornecedor e Cliente para composição de endereço e nacionalidade.

```mermaid
classDiagram
    class Pais {
        +Long id
        +String nome
        +String sigla
        +String codigo
        +String nacionalidade
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Estado {
        +Long id
        +String nome
        +String uf
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Cidade {
        +Long id
        +String nome
        +String ddd
        +String codigoIbge
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }

    Estado "*" --> "1" Pais : pertence a
    Cidade "*" --> "1" Estado : pertence a
```

---

## 8.2 Módulo RH e Usuário

Cargo define a função; Funcionário é a pessoa contratada (com endereço e nacionalidade); Usuário é a credencial de acesso, vinculada opcionalmente a um Funcionário (1:1).

```mermaid
classDiagram
    class Cargo {
        +Long id
        +String nome
        +String descricao
        +BigDecimal salarioBase
        +BigDecimal cargaHoraria
        +Boolean requerCnh
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Funcionario {
        +Long id
        +String nome
        +String apelido
        +TipoPessoa tipo
        +String cpf
        +String rgInscricaoEstadual
        +LocalDate dataNascimento
        +Sexo sexo
        +EstadoCivil estadoCivil
        +String telefone
        +String email
        +String endereco
        +String numero
        +String complemento
        +String bairro
        +String cep
        +String cnh
        +LocalDate dataValidadeCnh
        +LocalDate dataAdmissao
        +LocalDate dataDemissao
        +BigDecimal salario
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Usuario {
        +Long id
        +String nome
        +String login
        +String senha
        +String email
        +PerfilUsuario perfil
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class TipoPessoa {
        <<enumeration>>
        FISICA
        JURIDICA
    }
    class Sexo {
        <<enumeration>>
        MASCULINO
        FEMININO
        OUTRO
    }
    class EstadoCivil {
        <<enumeration>>
        SOLTEIRO
        CASADO
        SEPARADO
        DIVORCIADO
        VIUVO
        OUTRO
    }
    class PerfilUsuario {
        <<enumeration>>
        ADMIN
        OPERADOR
    }

    Funcionario "*" --> "1" Cargo : ocupa
    Funcionario "*" --> "0..1" Cidade : reside em
    Funcionario "*" --> "0..1" Pais : tem nacionalidade
    Usuario "0..1" --> "0..1" Funcionario : autentica
    Funcionario ..> TipoPessoa
    Funcionario ..> Sexo
    Funcionario ..> EstadoCivil
    Usuario ..> PerfilUsuario
```

---

## 8.3 Módulo Pessoas (Cliente e Fornecedor)

Cliente é o dono do plano mensalista e destinatário de notas de saída/serviço. Fornecedor abastece a conveniência e gera contas a pagar; possui múltiplos e-mails e telefones (composição, `ON DELETE CASCADE`).

```mermaid
classDiagram
    class Cliente {
        +Long id
        +String nome
        +String apelido
        +TipoPessoa tipo
        +String documento
        +String rgInscricaoEstadual
        +LocalDate dataNascimento
        +Sexo sexo
        +EstadoCivil estadoCivil
        +String telefone
        +String email
        +String endereco
        +String numero
        +String complemento
        +String bairro
        +String cep
        +BigDecimal limiteCredito
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Fornecedor {
        +Long id
        +String nome
        +String nomeFantasia
        +String apelido
        +TipoPessoa tipo
        +String documento
        +String rgInscricaoEstadual
        +String endereco
        +String numero
        +String complemento
        +String bairro
        +String cep
        +BigDecimal limiteCredito
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class FornecedorEmail {
        +Long id
        +String email
        +TipoEmail tipo
        +Boolean principal
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class FornecedorTelefone {
        +Long id
        +String telefone
        +TipoTelefone tipo
        +Boolean principal
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class TipoEmail {
        <<enumeration>>
        COMERCIAL
        FINANCEIRO
        COMPRAS
        VENDAS
        SUPORTE
    }
    class TipoTelefone {
        <<enumeration>>
        COMERCIAL
        RESIDENCIAL
        CELULAR
        FAX
        WHATSAPP
    }

    Cliente "*" --> "0..1" Cidade : reside em
    Cliente "*" --> "0..1" Pais : tem nacionalidade
    Cliente "*" --> "0..1" CondicaoPagamento : usa
    Fornecedor "*" --> "0..1" Cidade : reside em
    Fornecedor "*" --> "0..1" Pais : tem nacionalidade
    Fornecedor "*" --> "0..1" CondicaoPagamento : usa
    Fornecedor "1" *-- "*" FornecedorEmail : possui
    Fornecedor "1" *-- "*" FornecedorTelefone : possui
    FornecedorEmail ..> TipoEmail
    FornecedorTelefone ..> TipoTelefone
```

---

## 8.4 Módulo Pagamento

Forma de Pagamento (Dinheiro, Cartão, Pix, Boleto) e Condição de Pagamento (à vista, 30/60/90), esta última detalhada por suas Parcelas, que opcionalmente fixam uma forma específica.

```mermaid
classDiagram
    class FormaPagamento {
        +Long id
        +String nome
        +String descricao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class CondicaoPagamento {
        +Long id
        +String nome
        +Integer numeroParcelas
        +BigDecimal percentualJuros
        +BigDecimal percentualMulta
        +BigDecimal percentualDesconto
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class ParcelaCondicaoPagamento {
        +Long id
        +Integer numero
        +Integer dias
        +BigDecimal percentual
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }

    CondicaoPagamento "1" *-- "*" ParcelaCondicaoPagamento : detalha
    ParcelaCondicaoPagamento "*" --> "0..1" FormaPagamento : usa
```

---

## 8.5 Módulo Pátio

Núcleo operacional: Veículo (do Cliente quando mensalista; avulso quando sem dono), Tarifa (preços vigentes), Mensalista (contrato Cliente+Veículo) e Movimentação (o ticket de entrada/saída).

```mermaid
classDiagram
    class Veiculo {
        +Long id
        +String placa
        +String modelo
        +String cor
        +TipoVeiculo tipo
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Tarifa {
        +Long id
        +String descricao
        +BigDecimal valorHora
        +BigDecimal valorDiaria
        +Integer toleranciaMin
        +BigDecimal valorMensalidade
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Mensalista {
        +Long id
        +BigDecimal valorMensal
        +LocalDate dataInicio
        +LocalDate dataFim
        +StatusMensalista status
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Movimentacao {
        +Long id
        +TipoMovimentacao tipo
        +LocalDateTime dataEntrada
        +LocalDateTime dataSaida
        +BigDecimal valorCobrado
        +StatusMovimentacao status
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class TipoVeiculo {
        <<enumeration>>
        CARRO
        MOTO
    }
    class StatusMensalista {
        <<enumeration>>
        ATIVO
        SUSPENSO
        CANCELADO
    }
    class TipoMovimentacao {
        <<enumeration>>
        AVULSO
        MENSALISTA
    }
    class StatusMovimentacao {
        <<enumeration>>
        ABERTO
        FECHADO
        CANCELADO
    }

    Veiculo "*" --> "0..1" Cliente : pertence a
    Mensalista "*" --> "1" Cliente : titular
    Mensalista "*" --> "1" Veiculo : cobre
    Movimentacao "*" --> "1" Veiculo : registra
    Movimentacao "*" --> "0..1" Mensalista : vincula contrato
    Movimentacao "*" --> "0..1" Tarifa : aplica
    Veiculo ..> TipoVeiculo
    Mensalista ..> StatusMensalista
    Movimentacao ..> TipoMovimentacao
    Movimentacao ..> StatusMovimentacao
```

---

## 8.6 Módulo Conveniência

Cadastros de apoio do comercial (Categoria, Marca, Unidade de Medida, Serviço) e o Produto com estoque, que se associa a múltiplos Fornecedores via tabela de junção (N:N).

```mermaid
classDiagram
    class Categoria {
        +Long id
        +String nome
        +String descricao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Marca {
        +Long id
        +String nome
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class UnidadeMedida {
        +Long id
        +String nome
        +String sigla
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Servico {
        +Long id
        +String nome
        +String descricao
        +BigDecimal valorPadrao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class Produto {
        +Long id
        +String nome
        +String codigoBarras
        +String referencia
        +BigDecimal valorCompra
        +BigDecimal valorVenda
        +BigDecimal percentualLucro
        +BigDecimal quantidade
        +BigDecimal quantidadeMinima
        +String descricao
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class ProdutoFornecedor {
        +Long id
        +String codigoProd
        +BigDecimal custo
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }

    Produto "*" --> "1" Marca : tem
    Produto "*" --> "1" UnidadeMedida : medido em
    Produto "*" --> "0..1" Categoria : classificado em
    ProdutoFornecedor "*" --> "1" Produto : refere
    ProdutoFornecedor "*" --> "1" Fornecedor : refere
```

---

## 8.7 Módulo Fiscal

Notas fiscais e seus itens. Nota de Entrada (compra → +estoque) e Nota de Saída (venda → -estoque) compõem seus itens em cascata. Nota de Serviço (NFS-e do estacionamento, com ISS) tem origem em uma Movimentação avulsa ou em um Mensalista.

```mermaid
classDiagram
    class NotaEntrada {
        +Long id
        +String numero
        +String modelo
        +String serie
        +LocalDate dataEmissao
        +LocalDate dataChegada
        +TipoFrete tipoFrete
        +BigDecimal valorProdutos
        +BigDecimal valorFrete
        +BigDecimal valorSeguro
        +BigDecimal outrasDespesas
        +BigDecimal valorDesconto
        +BigDecimal valorTotal
        +SituacaoNota situacao
        +String observacao
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class ItemNotaEntrada {
        +Long id
        +Integer sequencia
        +BigDecimal quantidade
        +BigDecimal valorUnitario
        +BigDecimal valorDesconto
        +BigDecimal percentualDesconto
        +BigDecimal valorTotal
        +BigDecimal rateioFrete
        +BigDecimal rateioSeguro
        +BigDecimal rateioOutras
        +BigDecimal custoPrecoFinal
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class NotaSaida {
        +Long id
        +String numero
        +String modelo
        +String serie
        +LocalDate dataEmissao
        +LocalDate dataSaida
        +TipoFrete tipoFrete
        +BigDecimal valorProdutos
        +BigDecimal valorFrete
        +BigDecimal valorSeguro
        +BigDecimal outrasDespesas
        +BigDecimal valorDesconto
        +BigDecimal valorTotal
        +SituacaoNota situacao
        +String observacao
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class ItemNotaSaida {
        +Long id
        +Integer sequencia
        +BigDecimal quantidade
        +BigDecimal valorUnitario
        +BigDecimal valorDesconto
        +BigDecimal percentualDesconto
        +BigDecimal valorTotal
        +BigDecimal rateioFrete
        +BigDecimal rateioSeguro
        +BigDecimal rateioOutras
        +BigDecimal custoPrecoFinal
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class NotaServico {
        +Long id
        +String numero
        +String modelo
        +String serie
        +LocalDate dataEmissao
        +BigDecimal valorServico
        +BigDecimal aliquotaIss
        +BigDecimal valorIss
        +BigDecimal valorDesconto
        +BigDecimal valorTotal
        +SituacaoNotaServico situacao
        +String observacao
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class TipoFrete {
        <<enumeration>>
        CIF
        FOB
        SEM
    }
    class SituacaoNota {
        <<enumeration>>
        PENDENTE
        CONFIRMADA
        CANCELADA
    }
    class SituacaoNotaServico {
        <<enumeration>>
        PENDENTE
        EMITIDA
        CANCELADA
    }

    NotaEntrada "*" --> "1" Fornecedor : de
    NotaEntrada "*" --> "0..1" CondicaoPagamento : usa
    NotaEntrada "1" *-- "*" ItemNotaEntrada : compoe
    ItemNotaEntrada "*" --> "1" Produto : refere
    NotaSaida "*" --> "1" Cliente : para
    NotaSaida "*" --> "0..1" CondicaoPagamento : usa
    NotaSaida "1" *-- "*" ItemNotaSaida : compoe
    ItemNotaSaida "*" --> "1" Produto : refere
    NotaServico "*" --> "1" Cliente : para
    NotaServico "*" --> "0..1" Servico : tipifica
    NotaServico "*" --> "0..1" Movimentacao : origem avulsa
    NotaServico "*" --> "0..1" Mensalista : origem contrato
    NotaServico "*" --> "0..1" CondicaoPagamento : usa
    NotaServico "*" --> "0..1" FormaPagamento : usa
    NotaEntrada ..> TipoFrete
    NotaEntrada ..> SituacaoNota
    NotaSaida ..> TipoFrete
    NotaSaida ..> SituacaoNota
    NotaServico ..> SituacaoNotaServico
```

---

## 8.8 Módulo Financeiro

Títulos a pagar e a receber. Conta a Pagar nasce de Nota de Entrada (ou avulsa); Conta a Receber nasce de Nota de Saída ou de Mensalista; Conta a Pagar Avulsa cobre despesas sem nota (aluguel, luz, água, salário).

```mermaid
classDiagram
    class ContaPagar {
        +Long id
        +Integer numeroParcela
        +Integer totalParcelas
        +BigDecimal valorOriginal
        +BigDecimal valorPago
        +BigDecimal valorDesconto
        +BigDecimal valorJuros
        +BigDecimal valorMulta
        +BigDecimal valorTotal
        +LocalDate dataEmissao
        +LocalDate dataVencimento
        +LocalDate dataPagamento
        +SituacaoContaPagar situacao
        +String observacao
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class ContaReceber {
        +Long id
        +Integer numeroParcela
        +Integer totalParcelas
        +BigDecimal valorOriginal
        +BigDecimal valorRecebido
        +BigDecimal valorDesconto
        +BigDecimal valorJuros
        +BigDecimal valorMulta
        +BigDecimal valorTotal
        +LocalDate dataEmissao
        +LocalDate dataVencimento
        +LocalDate dataRecebimento
        +SituacaoContaReceber situacao
        +String observacao
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class ContasPagarAvulsa {
        +Long id
        +String descricao
        +String numeroNota
        +String modelo
        +String serie
        +Integer numeroParcela
        +Integer totalParcelas
        +BigDecimal valorOriginal
        +BigDecimal valorPago
        +BigDecimal valorDesconto
        +BigDecimal valorJuros
        +BigDecimal valorMulta
        +BigDecimal valorTotal
        +LocalDate dataEmissao
        +LocalDate dataVencimento
        +LocalDate dataPagamento
        +SituacaoContaPagar situacao
        +String observacao
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class SituacaoContaPagar {
        <<enumeration>>
        PENDENTE
        PARCIAL
        PAGA
        CANCELADA
    }
    class SituacaoContaReceber {
        <<enumeration>>
        PENDENTE
        PARCIAL
        RECEBIDA
        CANCELADA
    }

    ContaPagar "*" --> "1" Fornecedor : deve a
    ContaPagar "*" --> "0..1" NotaEntrada : origem
    ContaPagar "*" --> "0..1" CondicaoPagamento : usa
    ContaPagar "*" --> "0..1" FormaPagamento : usa
    ContaReceber "*" --> "1" Cliente : cobra de
    ContaReceber "*" --> "0..1" NotaSaida : origem
    ContaReceber "*" --> "0..1" Mensalista : origem contrato
    ContaReceber "*" --> "0..1" CondicaoPagamento : usa
    ContaReceber "*" --> "0..1" FormaPagamento : usa
    ContasPagarAvulsa "*" --> "0..1" Fornecedor : deve a
    ContasPagarAvulsa "*" --> "0..1" FormaPagamento : usa
    ContaPagar ..> SituacaoContaPagar
    ContaReceber ..> SituacaoContaReceber
    ContasPagarAvulsa ..> SituacaoContaPagar
```

---

## 8.9 Observações de Mapeamento Objeto-Relacional

- **Auditoria temporal:** `created_at` / `updated_at` mapeiam para `LocalDateTime criadoEm` / `atualizadoEm` (`TIMESTAMPTZ` no banco), tipicamente concentrados numa superclasse `@MappedSuperclass` com `@CreationTimestamp` / `@UpdateTimestamp`.
- **Enumerações:** os campos `VARCHAR` restritos por `CHECK` no schema são mapeados como `@Enumerated(EnumType.STRING)`, preservando os valores textuais (ex.: `status`, `situacao`, `tipo`, `perfil`).
- **Identidade:** todas as PKs usam `BIGINT GENERATED BY DEFAULT AS IDENTITY`, mapeadas como `Long id` com `@GeneratedValue(strategy = IDENTITY)`.
- **Cardinalidade `0..1`:** FKs anuláveis (sem `NOT NULL`) — associações `@ManyToOne(optional = true)`; FKs `NOT NULL` viram associações obrigatórias (`1`).
- **Composição:** itens de nota (`item_nota_entrada`, `item_nota_saida`) e contatos de fornecedor (`fornecedor_email`, `fornecedor_telefone`) têm `ON DELETE CASCADE`, justificando a composição UML (`*--`) e `cascade = ALL, orphanRemoval = true`.
- **Regras de coerência:** restrições como "no máximo 1 contrato ATIVO por veículo", "no máximo 1 movimentação ABERTA por veículo" e a exclusividade de origem em `nota_servico` / `conta_receber` são invariantes de domínio, validadas na camada de serviço além dos índices parciais e `CHECK` do banco.

## 8.10 Módulos relacionados

[[03-der]] · [[07-dicionario-de-dados]] · [[04-requisitos-funcionais]] · [[02-analise-negocio]]

## 8.11 Histórico

| Data | Ação |
|------|------|
| 2026-06-15 | Criação da Seção 8 — Diagramas de Classe (UML) em Mermaid, 8 diagramas por módulo aterrados nas 31 tabelas de `database/schema.sql`. |
