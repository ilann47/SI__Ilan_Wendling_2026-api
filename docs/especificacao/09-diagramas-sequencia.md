> Links: [[README]] · [[05-casos-de-uso]] · [[08-diagramas-classe]]

# 9. Diagramas de Sequência

## 9.1. Apresentação

Este capítulo descreve, por meio de **Diagramas de Sequência** (notação UML, sintaxe Mermaid `sequenceDiagram`), o comportamento dinâmico do sistema **Estacionamento Kaneko** para os fluxos de negócio mais relevantes. Cada diagrama explicita a colaboração entre os objetos ao longo do tempo, respeitando a arquitetura em camadas prevista para a implementação em **Spring MVC**:

1. **Ator** (Operador / Caixa / Gerente / Administrador) — origina a requisição pela interface.
2. **Controller (REST)** — recebe a requisição HTTP, valida o contrato de entrada (DTO) e delega à camada de serviço.
3. **Service** — concentra as regras de negócio, o controle transacional (`@Transactional`) e a orquestração entre repositórios.
4. **Repository (JPA)** — abstrai o acesso a dados via Spring Data JPA / Hibernate.
5. **Banco** — instância PostgreSQL 17 (`estacionamento_kaneko`), onde residem as restrições de integridade (`CHECK`, `UNIQUE`, índices parciais) definidas em `database/schema.sql`.

A nomenclatura dos artefatos (tabelas, colunas, constraints e índices) referenciados nos diagramas é **idêntica** à do esquema físico, garantindo rastreabilidade direta entre a documentação e o código.

### 9.1.1. Convenções de leitura

- Mensagem de **chamada síncrona**: seta cheia `->>`.
- Mensagem de **retorno**: seta tracejada `-->>`.
- Blocos condicionais e iterativos: `alt` / `else` (escolha exclusiva), `opt` (trecho opcional), `loop` (repetição).
- `Note over X`: observação sobre regra de negócio ou restrição de banco aplicável no ponto.

---

## 9.2. Fluxo 1 — Registrar Entrada de Veículo

Registra o ingresso de um veículo no pátio, criando uma `movimentacao` com `status = 'ABERTO'`. A unicidade de pátio é garantida pelo índice parcial **`uq_mov_veiculo_aberta`**, que impede duas movimentações abertas para o mesmo `veiculo_id`. O serviço executa a checagem antecipada (fail-fast) e ainda assim trata a violação de constraint como salvaguarda de concorrência.

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador
    participant Ctrl as MovimentacaoController
    participant Svc as MovimentacaoService
    participant Repo as MovimentacaoRepository
    participant DB as Banco

    Op->>Ctrl: POST /api/movimentacoes/entrada (placa, tipo)
    Ctrl->>Svc: registrarEntrada(dto)
    Svc->>Repo: findByVeiculoPlaca(placa)
    Repo->>DB: SELECT veiculo WHERE placa = ?
    DB-->>Repo: veiculo
    Repo-->>Svc: veiculo

    Note over Svc,DB: Checagem antecipada da regra uq_mov_veiculo_aberta
    Svc->>Repo: existsByVeiculoIdAndStatus(veiculoId, 'ABERTO')
    Repo->>DB: SELECT count(*) FROM movimentacao WHERE veiculo_id = ? AND status = 'ABERTO'
    DB-->>Repo: total
    Repo-->>Svc: jaAberta (boolean)

    alt Já existe movimentação ABERTA para o veículo
        Svc-->>Ctrl: throw RegraNegocioException("Veículo já está no pátio")
        Ctrl-->>Op: 409 Conflict
    else Veículo livre para entrada
        alt tipo = MENSALISTA
            Svc->>Repo: findContratoAtivo(veiculoId)
            Repo->>DB: SELECT * FROM mensalista WHERE veiculo_id = ? AND status = 'ATIVO'
            DB-->>Repo: mensalista
            Repo-->>Svc: mensalista
            Note over Svc: tipo='MENSALISTA' exige mensalista_id (ck_mov_tipo_coerencia); tarifa_id permanece nulo
        else tipo = AVULSO
            Svc->>Repo: findTarifaAtiva()
            Repo->>DB: SELECT * FROM tarifa WHERE ativo = TRUE
            DB-->>Repo: tarifa
            Repo-->>Svc: tarifa
            Note over Svc: tipo='AVULSO' não informa mensalista_id; vincula tarifa_id vigente
        end

        Svc->>Repo: save(movimentacao status='ABERTO', data_entrada=now())
        Repo->>DB: INSERT INTO movimentacao (...)
        alt Violação de uq_mov_veiculo_aberta (concorrência)
            DB-->>Repo: erro unique constraint
            Repo-->>Svc: DataIntegrityViolationException
            Svc-->>Ctrl: throw RegraNegocioException("Veículo já está no pátio")
            Ctrl-->>Op: 409 Conflict
        else INSERT bem-sucedido
            DB-->>Repo: id gerado
            Repo-->>Svc: movimentacao persistida
            Svc-->>Ctrl: MovimentacaoResponse
            Ctrl-->>Op: 201 Created (ticket de entrada)
        end
    end
```

---

## 9.3. Fluxo 2 — Registrar Saída e Cálculo de Tarifa

Encerra a movimentação aberta, calcula o `valor_cobrado` e altera o `status` para `'FECHADO'`. O cálculo bifurca por `tipo`: **mensalista** não paga avulso (valor zero); **avulso** paga por horas cheias/fração ao `valor_hora`, com **teto** no `valor_diaria` por diária, e **cortesia** quando a permanência for inferior à `tolerancia_min` da `tarifa`.

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador
    participant Ctrl as MovimentacaoController
    participant Svc as MovimentacaoService
    participant Repo as MovimentacaoRepository
    participant DB as Banco

    Op->>Ctrl: POST /api/movimentacoes/{id}/saida
    Ctrl->>Svc: registrarSaida(id)
    Svc->>Repo: findById(id)
    Repo->>DB: SELECT * FROM movimentacao WHERE id = ?
    DB-->>Repo: movimentacao
    Repo-->>Svc: movimentacao

    alt status != 'ABERTO'
        Svc-->>Ctrl: throw RegraNegocioException("Movimentação não está aberta")
        Ctrl-->>Op: 409 Conflict
    else status = 'ABERTO'
        Note over Svc: dataSaida = now(); permanência = data_saida - data_entrada (ck_mov_saida: data_saida >= data_entrada)

        alt tipo = MENSALISTA
            Note over Svc: Contrato vigente cobre a estadia → valor_cobrado = 0
            Svc->>Svc: valorCobrado = 0.00
        else tipo = AVULSO
            Svc->>Repo: findTarifaById(tarifaId)
            Repo->>DB: SELECT valor_hora, valor_diaria, tolerancia_min FROM tarifa WHERE id = ?
            DB-->>Repo: tarifa
            Repo-->>Svc: tarifa

            alt minutosPermanencia <= tolerancia_min
                Note over Svc: Cortesia sob tolerância → valor_cobrado = 0
                Svc->>Svc: valorCobrado = 0.00
            else minutosPermanencia > tolerancia_min
                Note over Svc: horas = ceil(minutos / 60); bruto = horas * valor_hora
                Svc->>Svc: bruto = horasCheias * valor_hora
                alt bruto > valor_diaria (aplica teto por diária)
                    Svc->>Svc: valorCobrado = diarias * valor_diaria
                else bruto <= valor_diaria
                    Svc->>Svc: valorCobrado = bruto
                end
            end
        end

        Svc->>Repo: save(status='FECHADO', data_saida, valor_cobrado)
        Repo->>DB: UPDATE movimentacao SET status='FECHADO', data_saida=?, valor_cobrado=? WHERE id=?
        DB-->>Repo: ok
        Repo-->>Svc: movimentacao fechada
        Svc-->>Ctrl: SaidaResponse (valorCobrado)
        Ctrl-->>Op: 200 OK (comprovante de saída)
    end
```

---

## 9.4. UC401 — Compra pela Nota de Entrada

```mermaid
sequenceDiagram
    actor Op as Operador
    participant UI as Editor da Nota de Entrada
    participant API as NotaEntradaController
    participant Serv as NotaEntradaService
    participant DB as PostgreSQL
    participant Est as EstoqueService
    Op->>UI: Preencher dados, local e itens
    UI->>API: POST /api/notas-entrada
    API->>Serv: Validar identidade, datas e itens
    alt Emissão futura ou regra inválida
        Serv-->>UI: Rejeição sem salvar nota ou efeitos
    else Composição válida
        Serv->>DB: Salvar rascunho e parcelas
        API-->>UI: Identificador e ETag
        Op->>UI: Conferir valores e parcelas
        UI->>API: POST /api/notas-entrada/{id}/previa com If-Match
        API-->>UI: Itens, custo médio estimado e cronograma salvo
        Op->>UI: Confirmar entrada integral
        UI->>API: POST /api/notas-entrada/{id}/confirmacao com If-Match
        API->>Serv: Bloquear nota e validar revisão
        Serv->>Est: Registrar recebimento e custo médio por item
        Est->>DB: Atualizar saldo por local e gravar movimentos
        Serv->>DB: Gravar contas, situação e auditoria na mesma transação
        API-->>UI: Nota confirmada e nova versão
    end
```

Para importação, `ImportacaoNotaController` recebe o XML, valida data antes da persistência, e exige conciliação e conferência integral. O cronograma é congelado após a conferência; `POST /api/notas-entrada/importacoes/{id}/confirmacao` cria e efetiva a única Nota de Entrada na mesma transação. Nenhuma divergência física permite confirmação parcial.

## 9.5. UC402 e UC404 — Venda direta e reversão interna

```mermaid
sequenceDiagram
    actor Op as Operador
    participant UI as Editor ou detalhe da nota
    participant API as NotaSaidaController
    participant Serv as NotaSaidaService
    participant Est as EstoqueService
    participant DB as PostgreSQL
    Op->>UI: Registrar venda sem pedido
    UI->>API: POST /api/notas-saida
    API->>Serv: Validar chave do emitente e composição
    Serv->>DB: Salvar rascunho e parcelas
    API-->>UI: Nota pendente e ETag
    Op->>UI: Conferir dados e confirmar revisão salva
    UI->>API: POST /api/notas-saida/{id}/confirmacao com If-Match
    API->>Serv: Bloquear e validar nota
    Serv->>Est: Baixar saldo do local e registrar custo vigente
    alt Saldo insuficiente
        Serv-->>UI: Rejeitar confirmação e reverter a transação
    else Estoque disponível
        Est->>DB: Saldo e movimento por item
        Serv->>DB: Gravar recebíveis, situação e auditoria
        Serv-->>API: Resposta montada dentro da transação
        API-->>UI: Nota confirmada e nova versão
    end
```

```mermaid
sequenceDiagram
    actor Op as Operador autorizado
    participant UI as Detalhe
    participant API as ReversaoNotaController
    participant Serv as ReversaoNotaService
    participant DB as PostgreSQL
    Op->>UI: Informar motivo da reversão
    UI->>API: POST /api/notas-entrada/{id}/reversao ou /api/notas-saida/{id}/reversao
    API->>Serv: Validar permissões, If-Match e motivo
    Serv->>DB: Bloquear nota e títulos; conferir histórico
    alt Título com baixa ou razão incompatível
        Serv-->>UI: Rejeição sem efeitos
    else Reversão elegível
        Serv->>DB: Compensar razão, cancelar títulos e registrar auditoria
        API-->>UI: Nota cancelada com data e motivo da reversão
    end
```

## 9.6. Fluxo 5 — Emitir Nota de Serviço interna (cálculo de ISS)

A `nota_servico` documenta o serviço prestado (estacionamento avulso, mensalidade ou lavagem), com origem opcional em `movimentacao` **ou** `mensalista` (nunca ambas — `ck_nota_servico_origem`). Na emissão, calcula-se o `valor_iss` a partir da `aliquota_iss` sobre o `valor_servico` e a situação passa de `'PENDENTE'` para `'EMITIDA'`.

```mermaid
sequenceDiagram
    autonumber
    actor Cx as Caixa
    participant Ctrl as NotaServicoController
    participant Svc as NotaServicoService
    participant Repo as NotaServicoRepository
    participant DB as Banco

    Cx->>Ctrl: POST /api/notas-servico/{id}/emissao
    Ctrl->>Svc: emitir(id)
    Svc->>Repo: findById(id)
    Repo->>DB: SELECT * FROM nota_servico WHERE id = ?
    DB-->>Repo: notaServico
    Repo-->>Svc: notaServico

    alt situacao != 'PENDENTE'
        Svc-->>Ctrl: throw RegraNegocioException("Nota de serviço não está pendente")
        Ctrl-->>Cx: 409 Conflict
    else situacao = 'PENDENTE'
        Note over Svc: ck_nota_servico_origem: movimentacao_id e mensalista_id são mutuamente exclusivos

        alt aliquota_iss > 0
            Note over Svc: valor_iss = round(valor_servico * aliquota_iss / 100, 2)
            Svc->>Svc: calcularIss(valorServico, aliquotaIss)
        else aliquota_iss = 0
            Note over Svc: ck_nota_servico_iss_coerencia: alíquota zero → valor_iss = 0
            Svc->>Svc: valorIss = 0.00
        end

        Note over Svc,DB: valor_total = valor_servico - valor_desconto (ck_nota_servico_iss_base: valor_iss <= valor_servico)
        Svc->>Svc: valorTotal = valorServico - valorDesconto

        Svc->>Repo: save(situacao='EMITIDA', valor_iss, valor_total)
        Repo->>DB: UPDATE nota_servico SET situacao='EMITIDA', valor_iss=?, valor_total=? WHERE id=?
        alt Violação de constraint (ck_nota_servico_valores / iss_base)
            DB-->>Repo: erro check constraint
            Repo-->>Svc: DataIntegrityViolationException
            Svc-->>Ctrl: throw RegraNegocioException("Valores fiscais inconsistentes")
            Ctrl-->>Cx: 422 Unprocessable Entity
        else UPDATE bem-sucedido
            DB-->>Repo: ok
            Repo-->>Svc: nota emitida
            Svc-->>Ctrl: NotaServicoResponse (valorIss, valorTotal)
            Ctrl-->>Cx: 200 OK (nota interna emitida)
        end
    end
```

---

## 9.7. UC501 e UC502 — Registrar baixa financeira

```mermaid
sequenceDiagram
    actor Op as Operador financeiro
    participant UI as Detalhe do título
    participant API as Controller de contas
    participant Serv as Serviço de contas
    participant DB as PostgreSQL
    Op->>UI: Informar valor e data
    UI->>API: POST /{id}/baixa com valor, data e Idempotency-Key
    API->>Serv: Autorizar e bloquear título da organização
    Serv->>DB: Verificar chave e histórico
    alt Mesma chave e mesmos dados
        Serv-->>API: Estado atual sem novo lançamento
    else Saldo insuficiente ou mesma chave com dados diferentes
        Serv-->>API: Conflito sem efeitos
    else Baixa inédita válida
        Serv->>DB: Salvar baixa, saldo, situação e auditoria
        Serv-->>API: Resposta com origem e contraparte ainda na transação
    end
    API-->>UI: Resultado
    UI->>API: GET /{id}/baixas
    API-->>UI: Histórico do título
```

As bases são `/api/contas-pagar` e `/api/contas-receber`. A chave é opcional por compatibilidade no backend e utilizada pelo frontend. A baixa não movimenta conta bancária.

## 9.8. Fluxo 7 — Cadastro Genérico com Exclusão Lógica

Representa o padrão comum a todos os cadastros de apoio (`cliente`, `fornecedor`, `produto`, `tarifa`, `categoria`, `marca`, `transportadora`, `veiculo_frota`, etc.), que herdam o atributo `ativo BOOLEAN`. A exclusão **nunca** remove o registro fisicamente: realiza-se *soft delete* via `ativo = FALSE`, preservando a integridade referencial (`ON DELETE RESTRICT`) e o histórico. O diagrama ilustra cadastro/atualização e a exclusão lógica. Os cadastros do **Módulo de Logística** — `transportadora` e `veiculo_frota` — bem como o vínculo N:N `transportadora_veiculo_frota` (a frota) seguem exatamente este mesmo fluxo genérico, com chave natural única `uq_transportadora_documento`, `uq_veiculo_frota_placa` e `uq_transp_veiculo_frota`, respectivamente.

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador
    participant Ctrl as CadastroController
    participant Svc as CadastroService
    participant Repo as CadastroRepository
    participant DB as Banco

    Note over Op,DB: Padrão genérico para entidades com coluna "ativo" (ex.: cliente, fornecedor, produto, tarifa)

    Op->>Ctrl: POST /api/{entidade} (dados do cadastro)
    Ctrl->>Svc: salvar(dto)
    Svc->>Repo: existsByChaveNatural(documento/placa/nome)
    Repo->>DB: SELECT count(*) WHERE chave_natural = ?
    DB-->>Repo: total
    Repo-->>Svc: existe (boolean)

    alt Já existe registro com a mesma chave natural única
        Note over Svc: Restrição UNIQUE (ex.: uq_cliente_documento, uq_veiculo_placa)
        Svc-->>Ctrl: throw RegraNegocioException("Registro já cadastrado")
        Ctrl-->>Op: 409 Conflict
    else Chave natural disponível
        Svc->>Repo: save(entidade, ativo=TRUE)
        Repo->>DB: INSERT INTO {entidade} (..., ativo) VALUES (..., TRUE)
        DB-->>Repo: id gerado
        Repo-->>Svc: entidade persistida
        Svc-->>Ctrl: EntidadeResponse
        Ctrl-->>Op: 201 Created
    end

    Note over Op,DB: --- Exclusão lógica (soft delete) ---

    Op->>Ctrl: DELETE /api/{entidade}/{id}
    Ctrl->>Svc: inativar(id)
    Svc->>Repo: findById(id)
    Repo->>DB: SELECT * FROM {entidade} WHERE id = ?
    DB-->>Repo: entidade
    Repo-->>Svc: entidade

    alt Registro não encontrado
        Svc-->>Ctrl: throw RecursoNaoEncontradoException
        Ctrl-->>Op: 404 Not Found
    else Registro encontrado
        opt entidade já está inativa
            Note over Svc: Operação idempotente — nada a fazer
        end
        Svc->>Repo: save(ativo=FALSE)
        Repo->>DB: UPDATE {entidade} SET ativo=FALSE, updated_at=now() WHERE id=?
        DB-->>Repo: ok
        Repo-->>Svc: entidade inativada
        Note over Svc,DB: Registro preservado; consultas padrão filtram ativo = TRUE
        Svc-->>Ctrl: void
        Ctrl-->>Op: 204 No Content
    end
```

---

## 9.9. Considerações Finais

Os sete diagramas acima cobrem os fluxos críticos do sistema e servem de contrato comportamental para a implementação Spring MVC. Observam-se três princípios transversais:

1. **Defesa em profundidade** — as regras de negócio são verificadas na camada `Service` (fail-fast) e reforçadas pelas restrições do banco (`CHECK`, `UNIQUE`, índices parciais como `uq_mov_veiculo_aberta`, `uq_mensalista_veiculo_ativo`, `uq_conta_receber_parcela`), tratando-se as violações de integridade como salvaguarda de concorrência.
2. **Transacionalidade** — operações que afetam múltiplas tabelas (confirmação de notas, geração de parcelas, baixa de estoque) ocorrem sob `@Transactional`, garantindo atomicidade.
3. **Preservação de histórico** — a exclusão é sempre lógica (`ativo = FALSE`), em coerência com as políticas `ON DELETE RESTRICT` do esquema físico.

Estes diagramas devem ser mantidos sincronizados com os [[05-casos-de-uso]] (origem dos fluxos) e com os [[08-diagramas-classe]] (estrutura estática das classes de domínio e serviço).
