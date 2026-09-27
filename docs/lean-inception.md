# Lean Inception: Crivo

> Decisões de produto do Crivo no formato Lean Inception (Paulo Caroli):
> visão → escopo → personas → jornadas → funcionalidades → sequenciamento → MVP.

---

## 1. Visão do produto

**Para** recrutadores de empresas pequenas e médias que contratam por planilha e WhatsApp
**cujo** problema é perder candidatos bons no meio do caminho (ninguém ligou de volta, a vaga já estava preenchida, o candidato pediu acima do orçamento e ficou esquecido),
**o Crivo** é um sistema de recrutamento (ATS) enxuto
**que** faz a triagem automática pelo orçamento da vaga, seleciona candidatos até completar as vagas, controla as tentativas de contato e mostra o funil em um quadro Kanban.
**Diferente de** planilhas e de ATS caros e complexos,
**o nosso produto** tem regras explícitas e auditáveis: cada mudança de etapa passa por uma máquina de estados e fica registrada no histórico do candidato.

## 2. O produto É / NÃO É / FAZ / NÃO FAZ

| É | NÃO É |
|---|---|
| Um funil de recrutamento por vaga | Um portal de vagas público |
| Uma ferramenta do recrutador | Um sistema de folha de pagamento ou admissão |
| Regras de negócio claras e testadas | Uma IA que "escolhe" candidatos |

| FAZ | NÃO FAZ |
|---|---|
| Triagem pelo salário pretendido x orçamento | Análise de currículo por palavras-chave (ainda) |
| Seleção automática até completar as vagas | Agendamento no calendário (ainda) |
| Controle de até 3 tentativas de contato, com chamada do suplente | Envio de e-mail ou WhatsApp (ainda) |
| Kanban com arrastar e soltar, validado no servidor | Mover etapas que não fazem sentido |
| Histórico, funil e simulação do processo | Guardar dados sensíveis além do necessário (LGPD) |

## 3. Objetivos do produto

1. **Nenhum candidato esquecido:** todo candidato está numa etapa, e toda etapa tem um próximo passo possível.
2. **Regras explícitas:** a triagem, o limite de vagas e as 3 tentativas de contato são as mesmas no quadro, na API e nos testes.
3. **Visão do funil:** quantos se inscreveram, quantos foram selecionados, entrevistados e contratados.
4. **Fácil de avaliar:** sobe com um clique, com vagas e candidatos de exemplo e uma simulação do processo.

## 4. Personas

### Juliana, recrutadora de uma empresa de 80 pessoas (31 anos)
- **Comportamento:** cuida de 5 vagas ao mesmo tempo, liga para candidatos entre reuniões.
- **Necessidades:** saber de relance em que pé está cada vaga e quem precisa de ligação hoje.

### Ricardo, gestor da área que abriu a vaga (42 anos)
- **Comportamento:** aprova o orçamento e quer contratar rápido.
- **Necessidades:** ver o funil e saber se o orçamento está afastando candidatos.

### Paula, candidata (26 anos)
- **Comportamento:** se inscreve em várias vagas e às vezes não atende número desconhecido.
- **Necessidades:** não ser descartada sem uma tentativa justa de contato.

## 5. Jornadas

**Juliana preenche a vaga de Analista de Suporte (2 vagas, orçamento de R$ 3.500,00)**
1. Cadastra os candidatos (ou importa uma lista em CSV); cada um recebe na hora a recomendação da triagem.
2. Clica em "Selecionar": o Crivo percorre os inscritos em ordem de inscrição e seleciona os que cabem no orçamento, até completar 2.
3. Liga para os selecionados e registra cada tentativa. Paula não atende três vezes: o Crivo marca "sem contato" e chama o próximo da fila.
4. Arrasta os candidatos pelo quadro: entrevista, proposta, contratado. Ao completar 2 contratações, a vaga é encerrada sozinha.

**Ricardo acompanha o funil**
1. Abre a vaga e vê: 12 inscritos, 5 selecionados, 3 entrevistados, 2 contratados.
2. Vê que 6 candidatos pediram acima do orçamento e decide revisar a faixa salarial.

## 6. Funcionalidades e revisão técnica

| Funcionalidade | Esforço | Valor de negócio | Valor de UX |
|---|---|---|---|
| Vagas com orçamento e número de vagas | E | $$$ | ♥♥ |
| Inscrição com triagem automática (ligar, contraproposta, aguardar) | E | $$$ | ♥♥♥ |
| Seleção automática até completar as vagas | EE | $$$ | ♥♥ |
| Tentativas de contato (máximo 3) e chamada do suplente | EE | $$$ | ♥♥♥ |
| Máquina de estados das etapas | EE | $$$ | ♥ |
| Kanban com arrastar e soltar | EE | $$ | ♥♥♥ |
| Histórico do candidato | E | $$ | ♥♥ |
| Funil e indicadores da vaga | E | $$ | ♥♥♥ |
| Importar candidatos por CSV | E | $$ | ♥♥ |
| Simulação do processo (sorteio repetível) | E | $ | ♥♥♥ |
| Visão geral com "precisa de atenção hoje" | EE | $$$ | ♥♥♥ |
| Compatibilidade (requisitos x habilidades + orçamento) | E | $$$ | ♥♥♥ |
| Avaliação da entrevista e proposta com valor (contraproposta) | EE | $$$ | ♥♥ |
| Página pública de carreiras | EE | $$ | ♥♥♥ |
| Notas do recrutador | E | $$ | ♥♥ |
| Login de recrutadores | EE | $$ | ♥ |
| Agenda de entrevistas | EEE | $$ | ♥♥ |
| E-mail automático ao candidato | EE | $$ | ♥♥ |

## 7. Sequenciamento em ondas

| Onda | Funcionalidades | Situação |
|---|---|---|
| 1 | Vagas, inscrição com triagem, máquina de estados, histórico | Entregue |
| 2 | Seleção automática, tentativas de contato e suplente, encerramento automático da vaga | Entregue |
| 3 | Kanban, funil, importação CSV, simulação, atalho de um clique, CI | Entregue |
| 4 | Visão geral com pendências do dia, compatibilidade, avaliação da entrevista, proposta com contraproposta, notas, página de carreiras, novo visual | Entregue |
| 5 | Login de recrutadores e gestores | Próxima |
| 6 | Agenda de entrevistas e e-mails automáticos | Futuro |

## 8. MVP

**Hipótese:** um funil com regras explícitas (orçamento, limite de vagas, 3 tentativas de contato) reduz candidatos esquecidos e acelera o preenchimento das vagas.

**Ondas 1 a 4.** Validado quando:
- nenhuma mudança de etapa inválida é aceita (garantido pela máquina de estados e por testes com todas as combinações);
- a vaga nunca tem mais contratados que vagas;
- um candidato só vai para "sem contato" depois de 3 tentativas registradas, e o suplente é chamado na hora;
- nenhuma proposta sai sem avaliação da entrevista (nota mínima 3) nem acima do orçamento.
