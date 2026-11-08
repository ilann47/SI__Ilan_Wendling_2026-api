> Links: [[README]] · [[03-der]] · [[07-dicionario-de-dados]]

# 8. Diagramas de Classe (UML)

As seções 8.1 a 8.10 preservam a representação histórica de classes do **Estacionamento Kaneko**, derivada de `database/schema.sql` (34 tabelas), com atributos tipados no estilo Java/JPA e enumerações modeladas como classes `<<enumeration>>`. A seção **8.10.1** apresenta o recorte vigente das notas de mercadoria, estoque e contas multiempresa, em correspondência com o [[03-der]].

As associações refletem as chaves estrangeiras do banco, com rótulos de cardinalidade. Adotam-se as convenções:

- **Associação** (`-->`): relação por chave estrangeira navegável (ex.: `Estado "*" --> "1" Pais`).
- **Composição** (`*--`): o filho não existe sem o pai e é removido em cascata com ele — usado nos itens de nota fiscal (`ON DELETE CASCADE` no schema).
- **Dependência** (`..>`): vínculo opcional/fraco ou referência cruzada de origem.

Para legibilidade, os mapeamentos `LocalDateTime criadoEm` / `atualizadoEm` (colunas `created_at` / `updated_at`, presentes em quase todas as tabelas) e os identificadores `Long id` são exibidos nas classes, mas as auditorias temporais são omitidas nas classes onde poluiriam o diagrama sem agregar — permanecem implícitas via superclasse técnica de persistência. Os diagramas estão divididos por módulo funcional.

---

> 🖼️ **Diagrama completo do modelo histórico (alta resolução):** [`diagramas/completo.png`](diagramas/completo.png) — 14684×7854 px. Os PNGs por módulo também são históricos.

## 8.1 Módulo Geografia

Hierarquia de localização (País → Estado → Cidade), referenciada por Funcionário, Fornecedor e Cliente para composição de endereço.

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

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-1-geografia.png`](diagramas/8-1-geografia.png)

---

## 8.2 Módulo RH e Usuário

Cargo define a função; Funcionário é a pessoa contratada (com endereço); Usuário é a credencial de acesso, vinculada opcionalmente a um Funcionário (1:1).

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
    Usuario "0..1" --> "0..1" Funcionario : autentica
    Funcionario ..> TipoPessoa
    Funcionario ..> Sexo
    Funcionario ..> EstadoCivil
    Usuario ..> PerfilUsuario
```

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-2-rh-usuario.png`](diagramas/8-2-rh-usuario.png)

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
    Cliente "*" --> "0..1" CondicaoPagamento : usa
    Fornecedor "*" --> "0..1" Cidade : reside em
    Fornecedor "*" --> "0..1" CondicaoPagamento : usa
    Fornecedor "1" *-- "*" FornecedorEmail : possui
    Fornecedor "1" *-- "*" FornecedorTelefone : possui
    FornecedorEmail ..> TipoEmail
    FornecedorTelefone ..> TipoTelefone
```

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-3-pessoas.png`](diagramas/8-3-pessoas.png)

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

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-4-pagamento.png`](diagramas/8-4-pagamento.png)

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

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-5-patio.png`](diagramas/8-5-patio.png)

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

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-6-conveniencia.png`](diagramas/8-6-conveniencia.png)

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

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-7-fiscal.png`](diagramas/8-7-fiscal.png)

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

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-8-financeiro.png`](diagramas/8-8-financeiro.png)

---

## 8.9 Módulo de Logística

Modelo estático do módulo de logística (abastecimento da conveniência): a `Transportadora` e o `VeiculoFrota`, associados em N:N pelo `TransportadoraVeiculoFrota` (a frota), com o `Fornecedor` referenciando a sua transportadora padrão de frete.

```mermaid
classDiagram
    class Transportadora {
        +Long id
        +String nome
        +String nomeFantasia
        +TipoPessoa tipo
        +String documento
        +String rgInscricaoEstadual
        +String telefone
        +String email
        +String endereco
        +String numero
        +String complemento
        +String bairro
        +String cep
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class VeiculoFrota {
        +Long id
        +String placa
        +String modelo
        +String marca
        +Integer ano
        +BigDecimal capacidade
        +String observacao
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }
    class TransportadoraVeiculoFrota {
        +Long id
        +Boolean ativo
        +LocalDateTime criadoEm
        +LocalDateTime atualizadoEm
    }

    Transportadora "*" --> "0..1" Cidade : localizada em
    Transportadora "*" --> "0..1" CondicaoPagamento : usa
    TransportadoraVeiculoFrota "*" --> "1" Transportadora : de
    TransportadoraVeiculoFrota "*" --> "1" VeiculoFrota : refere
    Fornecedor "*" --> "0..1" Transportadora : frete por
```

> 🖼️ **Versão em imagem (PNG):** [`diagramas/8-9-logistica.png`](diagramas/8-9-logistica.png)

Observa-se que `VeiculoFrota` é uma classe **distinta** de `Veiculo` (Seção 8.4 — pátio): o primeiro representa o veículo de carga da transportadora; o segundo, o automóvel estacionado pelo cliente. Não há associação entre ambos.

## 8.10 Observações de Mapeamento Objeto-Relacional

- **Auditoria temporal:** `created_at` / `updated_at` mapeiam para `LocalDateTime criadoEm` / `atualizadoEm` (`TIMESTAMPTZ` no banco), tipicamente concentrados numa superclasse `@MappedSuperclass` com `@CreationTimestamp` / `@UpdateTimestamp`.
- **Enumerações:** os campos `VARCHAR` restritos por `CHECK` no schema são mapeados como `@Enumerated(EnumType.STRING)`, preservando os valores textuais (ex.: `status`, `situacao`, `tipo`, `perfil`).
- **Identidade:** todas as PKs usam `BIGINT GENERATED BY DEFAULT AS IDENTITY`, mapeadas como `Long id` com `@GeneratedValue(strategy = IDENTITY)`.
- **Cardinalidade `0..1`:** FKs anuláveis (sem `NOT NULL`) — associações `@ManyToOne(optional = true)`; FKs `NOT NULL` viram associações obrigatórias (`1`).
- **Composição:** itens de nota (`item_nota_entrada`, `item_nota_saida`) e contatos de fornecedor (`fornecedor_email`, `fornecedor_telefone`) têm `ON DELETE CASCADE`, justificando a composição UML (`*--`) e `cascade = ALL, orphanRemoval = true`.
- **Regras de coerência:** restrições como "no máximo 1 contrato ATIVO por veículo", "no máximo 1 movimentação ABERTA por veículo" e a exclusividade de origem em `nota_servico` / `conta_receber` são invariantes de domínio, validadas na camada de serviço além dos índices parciais e `CHECK` do banco.

### 8.10.1 Recorte vigente — agregados e efeitos de notas de mercadoria

Itens e parcelas de conferência são componentes dos agregados fiscais. Os títulos, as baixas financeiras e o razão têm persistência própria: sua existência não se confunde com o rascunho ou com a visualização do documento.

```mermaid
classDiagram
    class NotaEntrada {
        +Long id
        +Long organizacaoId
        +Long version
        +String numero
        +String serie
        +String modelo
        +boolean cronogramaDefinido
        +BigDecimal valorTotal
        +SituacaoNota situacao
        +OffsetDateTime revertidaEm
        +String motivoReversao
    }
    class NotaSaida {
        +Long id
        +Long organizacaoId
        +Long version
        +String numero
        +String serie
        +String modelo
        +boolean cronogramaDefinido
        +BigDecimal valorTotal
        +SituacaoNota situacao
        +OffsetDateTime revertidaEm
        +String motivoReversao
    }
    class ItemNotaEntrada {
        +Integer sequencia
        +BigDecimal quantidade
        +BigDecimal custoTotal
        +BigDecimal custoMedioAnterior
        +BigDecimal custoMedioPosterior
    }
    class ItemNotaSaida {
        +Integer sequencia
        +BigDecimal quantidade
        +BigDecimal valorDesconto
        +BigDecimal custoPrecoFinal
    }
    class ParcelaNotaEntrada {
        +int numeroParcela
        +BigDecimal valor
        +LocalDate dataVencimento
        +String formaPagamentoNome
    }
    class ParcelaNotaSaida {
        +int numeroParcela
        +BigDecimal valor
        +LocalDate dataVencimento
        +String formaPagamentoNome
    }
    class ContaPagar {
        +Integer numeroParcela
        +BigDecimal valorOriginal
        +BigDecimal valorPago
    }
    class ContaReceber {
        +Integer numeroParcela
        +BigDecimal valorOriginal
        +BigDecimal valorRecebido
    }
    class BaixaFinanceira {
        +Long contaPagarId
        +Long contaReceberId
        +BigDecimal valor
        +LocalDate data
        +String chave
    }
    class SaldoEstoque {
        +Long organizacaoId
        +Long produtoId
        +Long localEstoqueId
        +BigDecimal quantidade
    }
    class MovimentoEstoque {
        +Long saldoEstoqueId
        +TipoMovimentoEstoque tipo
        +BigDecimal delta
        +BigDecimal saldoAnterior
        +BigDecimal saldoPosterior
        +BigDecimal saldoTotalAnterior
        +BigDecimal saldoTotalPosterior
        +BigDecimal custoUnitario
        +BigDecimal custoMedioAnterior
        +BigDecimal custoMedioPosterior
        +String origemTipo
        +String origemChave
        +Long compensadoId
    }
    class ReversaoNotaService {
        +entrada(org, ator, id, versao, motivo)
        +saida(org, ator, id, versao, motivo)
    }
    NotaEntrada "1" *-- "1..*" ItemNotaEntrada : contem
    NotaSaida "1" *-- "1..*" ItemNotaSaida : contem
    NotaEntrada "1" *-- "0..*" ParcelaNotaEntrada : congela
    NotaSaida "1" *-- "0..*" ParcelaNotaSaida : congela se avulsa
    NotaEntrada "0..*" --> "1" Fornecedor : emitente
    NotaSaida "0..*" --> "1" Cliente : destinatario
    NotaEntrada "0..*" --> "1" LocalEstoque : entrada fisica
    NotaSaida "0..*" --> "1" LocalEstoque : saida fisica
    ItemNotaEntrada "0..*" --> "1" Produto : referencia
    ItemNotaSaida "0..*" --> "1" Produto : referencia
    ParcelaNotaEntrada "0..*" --> "0..1" FormaPagamento : referencia
    ParcelaNotaSaida "0..*" --> "0..1" FormaPagamento : referencia
    ContaPagar "0..*" --> "0..1" NotaEntrada : origem fiscal
    ContaReceber "0..*" --> "0..1" NotaSaida : origem avulsa
    BaixaFinanceira ..> ContaPagar : contaPagarId
    BaixaFinanceira ..> ContaReceber : contaReceberId
    SaldoEstoque ..> LocalEstoque : localEstoqueId
    MovimentoEstoque ..> SaldoEstoque : saldoEstoqueId
    MovimentoEstoque ..> MovimentoEstoque : compensadoId
    MovimentoEstoque ..> NotaEntrada : origem logica
    MovimentoEstoque ..> NotaSaida : origem logica
    ReversaoNotaService ..> NotaEntrada : reverte entrada
    ReversaoNotaService ..> NotaSaida : reverte saida avulsa
    ReversaoNotaService ..> EstoqueService : compensa no razao
    ReversaoNotaService ..> ContaPagar : cancela sem baixa
    ReversaoNotaService ..> ContaReceber : cancela sem baixa
```

As multiplicidades de local e item representam as regras da nota no tenant, não uma obrigação de preenchimento dos registros históricos sem organização. Relações por `contaPagarId`, `contaReceberId`, `localEstoqueId`, `saldoEstoqueId` e `compensadoId` são referências escalares nas classes e possuem FKs no banco. Relações rotuladas **origem lógica** são resolvidas pelo serviço a partir de tipo e chave do movimento.

| Processo | Coordenação e efeito |
|---|---|
| Entrada | `NotaEntradaService` valida a emissão não futura no lançamento, na conferência e na confirmação; a importação XML usa a mesma regra. Calcula a conferência e salva parcelas previstas. Ao confirmar, registra recebimento por item, atualiza o custo médio ponderado e cria os títulos a pagar, em uma transação. |
| Saída avulsa | `NotaSaidaService` recalcula descontos e despesas, congela as parcelas no rascunho e exige a revisão vigente na confirmação contextual. O `EstoqueService` lê o custo médio sob lock, registra a baixa e devolve o custo usado para o item. O financeiro usa o cronograma salvo para gerar os títulos a receber. |
| Reversão | `ReversaoNotaService` aceita Entrada ou Saída avulsa confirmada com autorização e versão vigentes. Rejeita efeitos financeiros já baixados ou histórico físico que não possa ser compensado; mantém os movimentos originais, cria compensações e cancela os títulos elegíveis. O resultado é interno, sem cancelamento fiscal externo. |
| Baixa financeira | `ContaPagarService` e `ContaReceberService` validam o saldo sob lock e usam `BaixaFinanceiraService` para registrar histórico e idempotência. A resposta com a contraparte e a origem é montada dentro da transação. |

## 8.11 Módulos relacionados

[[03-der]] · [[07-dicionario-de-dados]] · [[04-requisitos-funcionais]] · [[02-analise-negocio]]

## 8.12 Histórico

| Data | Ação |
|------|------|
| 2026-06-15 | Criação da Seção 8 — Diagramas de Classe (UML) em Mermaid, 8 diagramas por módulo aterrados nas 31 tabelas de `database/schema.sql`. |
| 2026-07-03 | Inclusão da Seção 8.9 — Módulo de Logística (`Transportadora`, `VeiculoFrota`, `TransportadoraVeiculoFrota`) e da associação `Fornecedor → Transportadora`. |
| 2026-07-06 | Renderização dos 9 diagramas por módulo + 1 diagrama completo em PNG de alta resolução (`diagramas/`), com links a partir de cada bloco Mermaid. |
| 2026-11-05 | Acrescenta o UML vigente de agregados fiscais, cronogramas, baixas financeiras, razão e reversão interna; identifica os PNGs como históricos. |
