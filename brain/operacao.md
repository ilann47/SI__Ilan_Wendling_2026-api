> Links: [[core]] · [[unificacao-multitenant]] · [[clientes]] · [[rbac]] · [[auditoria]] · [[testes-multitenancy]]

# Operação

## Cobranças em centavos e movimentos de caixa — 2026-11-22

A V107 marca somente estadias registradas depois desta versão. Para essas
estadias, o cálculo continua em BigDecimal/seis casas; a obrigação efetiva é
arredondada a duas casas com HALF_UP e persistida como inteiro escalado de seis
casas. `patio_cobranca_centavos` conserva calculado, devido e diferença, sem
backfill ou alteração das notas/estadias antigas. Uma base positiva que feche
zero é rejeitada (409), inclusive ao configurar cobrança do evento.

No evento, os três valores são congelados na entrada, inclusive no modo SAIDA.
A OS automática e a pendência fiscal usam o devido; a saída pré-paga cobra zero.
No estacionamento comum, o marcador nasce na entrada e o snapshot financeiro
é confirmado na saída. Histórico sem marcador preserva seus valores originais.
O marcador nasce na fonte única `MovimentacaoService`, atendendo tanto o Caixa
quanto `POST /api/movimentacoes/entrada`. A saída CRUD contextual também aplica
o devido e confirma o snapshot, sem permitir contornar a regra pela rota
anterior. O CRUD preserva o conflito em uma segunda saída e não registra
pagamento ou receita de turno implicitamente. Compatibilidade sem organização
ativa (`organizacao_id IS NULL`) fica fora da operação contextual e não recebe
marcador com tenant inexistente.
As prévias mantêm validade e idempotência anteriores. `valorEsperado` significa
o valor efetivamente cobrado esperado: para entradas novas, é comparado com o
devido arredondado. A opção de evento também retorna `valor` como devido,
preservando o preço-base em `valorCalculado`. Assim clientes anteriores que
recarreguem a opção calculam o troco correto. Estado antigo com o preço-base
diferente do devido recebe 409, sem criar estadia, pagamento ou OS, e deve
atualizar os dados. Replay usa o valor devido congelado da estadia; histórico
sem snapshot mantém seu valor original de seis casas. PUT da configuração
continua recebendo o preço-base com seis casas.

Opções de evento, detalhes da estadia, prévia e comprovante acrescentam
`valorCalculado`, `valorDevido`, `diferencaArredondamento` como textos decimais.
Snapshots históricos são nulos. Em prévia/saída esses valores descrevem a
cobrança daquela saída; a segunda via conserva a obrigação econômica original.

### Contratos de caixa

- `POST /api/v1/patio/turnos/{id}/movimentos-dinheiro`, com
  `{chave: UUID, tipo: SUPRIMENTO|SANGRIA, valor: decimal6, motivo: texto10..300}`,
  retorna HTTP 200 com `Resumo` do turno. Exige `operations:checkout` ou
  `operations:manage`, turno próprio aberto e sangria não superior ao saldo.
  Repetição exata da chave recupera o resumo mesmo depois do fechamento;
  reutilização com outro conteúdo retorna 409. Não há transferência implícita.
- `GET /api/v1/patio/turnos/{id}/movimentos-dinheiro` retorna
  `{id,chave,tipo,valor,motivo,registradoEm,usuarioId,turnoId}[]`. Caixa consulta
  somente seu turno; supervisor `operations:manage` consulta qualquer turno da
  organização ativa. Outro tenant/turno inacessível retorna 404.
- `GET /api/v1/patio/turnos?pagina=0&tamanho=20`, exclusivo de
  `operations:manage`, retorna `{content,totalElements,pagina,tamanho}`;
  cada linha contém `{turnoId,operador,abertoEm,fechadoEm,resumo}`. Operador
  apresenta nome real, usando login somente se o nome estiver vazio.

`Resumo` preserva `id` e acrescenta `suprimentos`/`sangrias`. O esperado é
fundo + recebimentos líquidos em dinheiro + suprimentos - sangrias. Movimentos
de caixa não são receita. Fundo, recebido, troco e suprimento/sangria continuam
admitindo seis casas; apenas a nova cobrança efetiva fecha em centavos.
V107 armazena ator, motivo e chave, protege o ledger contra UPDATE/DELETE e
conserva o snapshot confirmado imutável. Diferenças de eventos incluem esses
movimentos uma vez por turno relevante, sem atribuí-los à receita do evento.

### Verificação desta evolução

`PatioCobrancaCentavosTest` cobre HALF_UP e preço positivo que fecharia zero;
`PatioMovimentoDinheiroTest` cobre saldo/precisão. `PatioCentavosMovimentosApiTest`
cobre pré/pós-pago, OS/fiscal, histórico sem marcador, comum novo pelo Caixa e
pelo CRUD contextual, idempotência,
saldo e RBAC. A suíte e o banco são executados exclusivamente pelo integrador
da tarefa; resultados novos devem ser registrados após execução, não inferidos
do resultado histórico V106 abaixo.

## Rotina de caixa — 2026-11-19

`POST /api/v1/patio/entradas` aceita placa ou identificador visível de 1 a 10
caracteres em `placa`, sem exigir o formato oficial. Normaliza maiúsculas e
espaços nas pontas; só remove o hífen de placas brasileiras completas.
Campo vazio, caracteres invisíveis de controle ou valor acima do limite
retornam 400. Saída rápida, comprovantes e correção usam a mesma regra.
A busca parcial compara texto literal, inclusive % e _, no contexto ativo.
Não altera cadastro geral de veículos, credenciais, turno, cobrança ou fiscal.

- O contexto de evento/pátio é preservado no frontend por organização e operador;
  o servidor continua validando tenant, janela, capacidade e preço a cada entrada.
- Entradas pagas e saídas aceitam `recebido` opcional somente em dinheiro.
  O valor entregue não pode ser inferior à cobrança. O troco usa BigDecimal de
  seis casas e snapshots `patio_pagamento_dinheiro` em inteiros de escala zero.
- `GET /api/v1/patio/busca-saida?placa=&patioId=&eventoId=` pesquisa no banco
  no máximo 20 estadias abertas. `GET /comprovantes?placa=` (sob o mesmo prefixo)
  lista os 50 últimos comprovantes e `GET /movimentacoes/{id}/comprovante`
  consulta a segunda via da origem. Leitura não cria pagamentos ou notas.
- `POST /api/v1/patio/movimentacoes/{id}/correcao` exige `operations:manage`,
  turno aberto, UUID idempotente, motivo de 10–300 caracteres e ação explícita.
  Corrigir placa troca só a referência da estadia aberta avulsa, não altera
  placas históricas de outros registros. Trocar meio e cancelar registram
  débitos/créditos assinados em `patio_operacao_ajuste`; os originais ficam.
- O cancelamento libera ocupação se aberto, cancela a OS automática e a
  pendência fiscal local ainda não emitida. Não gera recebível ou transmite
  cancelamento à prefeitura. Registro fiscal externo não pendente impede
  esta compensação local. Devoluções só são confirmadas após a ação real
  pelo operador; este sistema não executa Pix/cartão.
- O fechamento soma recebimentos e ajustes do turno, preservando períodos
  anteriores. `patio_operacao_correcao` e auditoria identificam autor e motivo.
- V106 acrescenta exclusivamente estruturas de controle operacional. Não
  recalcula documentos antigos nem remove recebimentos.

### Testes novos

`PatioDinheiroTest` cobre precisão, insuficiência e cancelamento legado sem OS.
`PatioCaixaApiTest` cobre dinheiro, segunda via, compensações, repetição,
tenant e bloqueio do Caixa para correções. `PatioEventoOperacaoApiTest` verifica
o percurso completo e os totais de [[eventos]] com financeiro/fiscal.

Validação final local: 937 testes em 158 classes passaram no PostgreSQL 17
isolado em 127.0.0.1:55475, usando uma base vazia com o nome de teste exigido
pelas proteções das migrações. Não foi necessário alterar esses guardas nem
apagar a base da prévia 55474. A primeira tentativa com outro nome foi bloqueada
pela V96 antes da suíte; a execução definitiva passou de V1 a V106.
O Chrome validou pré/pós-pago, troco, segunda via, mesma OS, pendência fiscal,
compensação, RBAC e fechamento pela 5174. As novas colunas monetárias da V106
foram conferidas no banco com escala zero. Nenhuma nota antiga foi recalculada.

## Entrada avulsa de evento no caixa

O evento em operação aceita placa sem pedido nem credencial. A cobrança fixa
usa `GET/PUT /api/v1/events/{id}/cobranca-patio`, com `modo: ENTRADA | SAIDA`
e `valor` positivo de até seis casas. A consulta exige `events:read`;
a configuração, `events:create`. Só entradas novas são afetadas.

`GET /api/v1/patio/caixa` fornece opções por evento/pátio e estadias abertas.
O POST de entrada exige `eventoId`, `patioId` e `chaveEntrada` (UUID). No
modo ENTRADA exige também `meio` e `valorEsperado`. Repetir a chave com
os mesmos dados recupera a estadia; alterar os dados ou submeter valor
desatualizado retorna 409.

A entrada ocupa a mesma alocação do QR e usa vagas fora das cotas vendável
e reservada. A saída usa o valor congelado e não cobra de novo quando já
pago na entrada. O recebimento soma no turno que o registrou.

Desde a V104, a entrada cria uma [[ordens-servico|OS]] em execução vinculada
à estadia, com o valor congelado e sem exigir cadastro de cliente. Na saída,
a mesma OS é concluída e recebe a pendência `PENDENTE_EMISSAO`. Não é NFS-e
autorizada e não cria recebível. O caixa continua como fonte do recebimento.
Os detalhes de evento retornam `ordemServicoId`, `ordemServicoNumero` e
`ordemServicoStatus`, consultáveis em
`GET /api/v1/patio/movimentacoes/{id}/fiscal-evento` sem ampliar o perfil Caixa.
Repetições da mesma entrada ou saída não duplicam OS nem pendência fiscal.
Estadias já encerradas não são alteradas; estadia antiga ainda aberta recebe
a OS no encerramento usando sua data de entrada. Consulte [[eventos]] e
[[fiscal-financeiro]].

Com a V110, para novos eventos a OS congela o nome e o ID do serviço já
escolhido pelo organizador. O caixa não escolhe nem cadastra serviços e o
preço continua vindo da cobrança configurada no evento. O rascunho lê o
item da mesma OS, sem completar documentos históricos.

Na V108, o POST de entrada (pré-pago) ou saída (pós-pago) recebe
`tomador:{documento,nome}` opcional quando há pagamento do evento.
Aceita CPF e CNPJ numérico ou alfanumérico válidos, em formato canônico
ou com máscara. O caixa pode informar os dados, mas o recebimento e o
comprovante não devolvem a identificação. Na V109, quando identificado, o
tomador passa a ser cadastrado como [[clientes|cliente básico]] (ou
reaproveitado pelo documento), sem ligar o veículo nem criar contrato.
O snapshot privado do pagamento não muda após a edição fiscal, então
repetir uma confirmação não recria a nota nem exige os dados editados.
O rascunho editável nasce ao concluir a mesma estadia e é consultável
mesmo antes de o evento terminar, via [[fiscal-financeiro]].

## Objetivo

Manter os cadastros e fluxos operacionais do estacionamento isolados pela organização ativa.

## Contexto

As V58 a V61 migram `Veiculo`, `Tarifa`, `Mensalista` e `Movimentacao` para o contexto organizacional.

## Fluxo (camadas da arquitetura)

`VeiculoController` valida o JWT contextual e o RBAC, `VeiculoService` aplica normalização e isolamento, `VeiculoRepository` consulta pelo owner e o PostgreSQL protege unicidade e referências do mesmo tenant.

No Pátio: abrir turno com fundo de troco, registrar placa antiga ou Mercosul,
conferir o valor antes de receber, confirmar a saída com o meio registrado e
fechar com o dinheiro contado. Com um pátio ativo ele é escolhido automaticamente;
com vários, o operador seleciona pelo nome. A entrada é vinculada ao local e
respeita a capacidade operacional. O fechamento não remove veículos estacionados.

## Endpoints

- `GET /api/veiculos[/{id}]`: requer `operations:read`.
- `POST/PUT/DELETE /api/veiculos[/{id}]`: requer `operations:manage`.
- `GET /api/tarifas[/{id}]`: requer `operations:read`.
- `POST/PUT/DELETE /api/tarifas[/{id}]`: requer `operations:manage`.
- `GET /api/mensalistas[/{id}]`: requer `operations:read`.
- Mutações e geração de mensalidade exigem `operations:manage`; o faturamento também exige `finance:manage`.
- `GET /api/v1/patio/caixa`: turno atual, último fechamento e pátios ativos.
- `POST /api/v1/patio/turnos` (fundoTroco), `POST /api/v1/patio/turnos/{id}/fechamento` (dinheiroContado).
- `POST /api/v1/patio/entradas` (placa, patioId quando houver mais de um).
- `POST /api/v1/patio/movimentacoes/{id}/previa-saida` congela a cotação por três minutos;
  `POST /api/v1/patio/movimentacoes/{id}/saida` exige turnoId, chave e meio. A mesma
  confirmação pode ser repetida sem registrar um segundo pagamento. Todos requerem
  `operations:checkout` ou `operations:manage` no contexto da organização.

## Estrutura de Dados (DTOs, Entidades)

`Veiculo` possui `organizacaoId` nullable apenas para compatibilidade histórica e `version` otimista. `VeiculoResponse` expõe ambos; `VeiculoRequest` não aceita owner.

V101 introduz `patio_turno`, com um turno aberto por operador e organização,
e `patio_checkout`, com uma saída por movimentação. Valores monetários são
`BigDecimal`, gravados em inteiros de escala zero (milionésimos) e devolvidos
como texto decimal no novo contrato para preservar seis casas no navegador.

## Integrações externas

Não há integração externa neste recorte.

## Tratamento de Erros

Recurso ou Cliente fora do tenant retorna `404`; placa duplicada no mesmo tenant retorna `409`; ausência de autenticação retorna `401` e contexto/permissão insuficiente retorna `403`.

## Testes

`VeiculoTenantApiTest` cobre contexto obrigatório, normalização, duplicidade entre tenants, segregação, referência cruzada e auditoria. A suíte legada de veículo, mensalista e movimentação preserva compatibilidade.

`PatioCaixaApiTest` cobre placa, lotação, isolamento, pagamento idempotente,
conciliação com seis casas e mensalista que sai em outro turno sem cobrança.
`MensalistaTenantApiTest` verifica que o vínculo com cadastro fiscal básico
precisa de telefone ou e-mail válido para iniciar ou reativar o contrato.

## Decisões Técnicas

- Placa é normalizada e única por organização no service e no banco.
- Cliente vinculado deve possuir o mesmo owner.
- Mensalista e Movimentação continuam aceitando somente veículos históricos até suas migrations próprias.
- Ownership é imutável no PostgreSQL.
- A saída é contabilizada no turno que recebeu, não no turno que registrou a entrada.
  O dinheiro esperado é fundo mais recebimentos líquidos em dinheiro, suprimentos
  menos sangrias; Pix e cartão
  ficam separados. Saídas sem cobrança contam como saídas, não como recebimentos.
- Registrar pagamento não aciona Pix, maquininha, Nota de Serviço nem Conta a Receber.
  Eventos geram OS automática por estadia; permanência comum não gera OS manual.
- O papel Caixa do pátio não recebe acesso administrativo a tarifas ou cadastros.

## Módulos relacionados

[[clientes]] · [[rbac]] · [[auditoria]] · [[unificacao-multitenant]]

## Histórico

- 2026-11-22 — V110 leva o serviço selecionado no evento à OS da estadia, sem alterar preço ou recebimento.

| Data | Ação |
|---|---|
| 2026-11-22 | V109 cadastra tomador de evento no pagamento, sem alterar cliente existente, sem cobrança adicional. |
| 2026-11-22 | V108 aceita identificação fiscal opcional no pagamento do evento e disponibiliza o rascunho da mesma estadia. |
| 2026-11-22 | Compatibilidade segura: opção de evento e valorEsperado representam devido; base fica em valorCalculado e estado pré-deploy divergente recebe 409 sem efeitos. |
| 2026-11-22 | Marcador de novas estadias comuns centralizado em MovimentacaoService; saída CRUD contextual aplica centavos e snapshot sem recalcular histórico ou duplicar o marcador dos eventos. |
| 2026-11-22 | V107 aplica centavos HALF_UP somente a novas estadias, conserva snapshots e implementa suprimento/sangria auditáveis e supervisão de turnos por organização. |
| 2026-11-09 | V104 abre uma OS por estadia de evento, conclui na saída e vincula a pendência fiscal. |
| 2026-11-09 | V103 liga eventos ao caixa com entrada por placa, cobrança única e pendência fiscal ao encerrar. |
| 2026-11-09 | V101 implementa caixa por turno, entrada por placa e saída com pagamento no Pátio. |
| 2026-11-22 | Aceita placa ou identificador livre na entrada de portaria e preserva sua busca literal na saída e nos comprovantes. |
| 2026-11-09 | V102 adiciona papel de caixa sem privilégios administrativos. |
| 2026-08-04 | V58 migra Veículo e o frontend correspondente para tenancy contextual. |
| 2026-08-04 | V59 migra Tarifa preservando descrições repetidas permitidas pelo legado. |
| 2026-08-04 | V60 migra Mensalista e a geração tenant-aware de Conta a Receber. |
| 2026-08-04 | V61 migra entrada, saída, cancelamento e ocupação por pátio. |
| 2026-08-04 | Relatórios de pátio, faturamento e mensalistas passam a respeitar a organização ativa. |
| 2026-08-04 | Entrada, saída e cancelamento passam a registrar trilha operacional tenant-aware. |
| 2026-09-12 | Regressão de faturamento reforça o fallback legado com `organizacao_id IS NULL` nas movimentações fechadas; não altera a operação contextual ou o cálculo tarifário. |
