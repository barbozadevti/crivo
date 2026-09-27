// Ficha do candidato numa gaveta lateral: compatibilidade, dados, ações da etapa (contato, avaliação, proposta), notas e histórico.
import {
  $, $$, NOMES_ETAPAS, acabamento, anel, api, avatar, avisar, avisarErro, classeDaArea, dataHora, dinheiro,
  dinheiroExato, erroNoFormulario, escapar, estrelas, icone, lerValor,
} from "./ui.js";
import { encerrarCandidato, estado, mover, registrarContato } from "./quadro.js";

const ICONES_HISTORICO = { INSCRICAO: "mais", MUDANCA_DE_ETAPA: "seta", CONTATO: "telefone", AVALIACAO: "estrela", NOTA: "nota" };

export async function abrirCandidato(id, aoMudar, foco) {
  const gaveta = $("#gaveta");
  try {
    const { candidato: c, historico } = await api("/api/candidatos/" + id);
    const vaga = estado.vaga;
    const aberta = vaga?.status === "ABERTA";
    gaveta.innerHTML = `
      <header class="gaveta-capa degrade ${classeDaArea(vaga?.area)}">
        <button class="fechar" type="button" aria-label="Fechar">×</button>
        <p class="vaga-da-gaveta">${escapar(vaga?.titulo || "")}</p>
        <div class="pessoa">${avatar(c.nome, "grande")}<div><h2 id="gaveta-titulo">${escapar(c.nome)}</h2>
          <div class="chips"><span class="chip vidro">${NOMES_ETAPAS[c.etapa]}</span><span class="chip vidro">${escapar(c.origemNome)}</span>
            <span class="chip vidro">${c.diasNaEtapa}d na etapa</span></div></div></div>
        <div class="links"><a class="chip vidro" href="mailto:${escapar(c.email)}">${icone("email")} ${escapar(c.email)}</a>
          ${c.telefone ? `<span class="chip vidro">${icone("telefone")} ${escapar(c.telefone)}</span>` : ""}
          ${c.linkedin ? `<a class="chip vidro" href="${escapar(c.linkedin)}" target="_blank" rel="noopener">${icone("linkedin")} LinkedIn</a>` : ""}</div>
      </header>
      <div class="gaveta-corpo">
        <div class="abas" role="tablist"><button class="aba" role="tab" aria-selected="true" data-aba="resumo">Resumo</button>
          <button class="aba" role="tab" aria-selected="false" data-aba="historico">Histórico (${historico.length})</button></div>
        <div data-painel="resumo" class="secao">
          <div class="compat">${anel(c.compatibilidade.pontuacao, "grande")}<div class="secao">
            <strong>Compatibilidade com a vaga</strong>
            <div class="chips">${c.compatibilidade.atende.map((r) => `<span class="chip ok">✓ ${escapar(r)}</span>`).join("")}
              ${c.compatibilidade.faltam.map((r) => `<span class="chip falta">${escapar(r)}</span>`).join("")}</div>
            <small class="suave">80% pelos requisitos atendidos e 20% pelo encaixe no orçamento.</small></div></div>
          ${acoesDaEtapa(c, aberta)}
          <div class="secao"><h3>Dados</h3><div class="ficha">
            <div><small>Pretensão</small>${dinheiro(c.salarioPretendido)}</div>
            <div><small>Orçamento da vaga</small>${vaga ? dinheiro(vaga.salarioBase) : "—"}</div>
            <div><small>Triagem</small><span class="selo ${c.recomendacao}">${escapar(c.recomendacaoTexto)}</span></div>
            <div><small>Inscrição</small>${dataHora(c.inscritoEm)}</div>
            ${c.notaEntrevista ? `<div><small>Entrevista</small>${estrelas(c.notaEntrevista)} ${c.notaEntrevista}/5</div>` : ""}
            ${c.salarioOfertado ? `<div><small>Proposta enviada</small>${dinheiroExato(c.salarioOfertado)}</div>` : ""}
          </div>
          ${c.parecer ? `<p class="suave">“${escapar(c.parecer)}”</p>` : ""}
          ${c.habilidades.length ? `<h3>Habilidades</h3><div class="chips">${c.habilidades.map((h) => `<span class="chip">${escapar(h)}</span>`).join("")}</div>` : ""}</div>
          <form class="secao" data-anotar novalidate><h3>Anotar</h3>
            <div class="acoes"><input class="campo-nota" name="texto" maxlength="500" placeholder="Ex.: pediu retorno na segunda">
            <button class="botao pequeno">${icone("nota")} Salvar nota</button></div></form>
        </div>
        <div data-painel="historico" hidden><ol class="historico">${[...historico].reverse().map((h) => `<li class="h-${h.tipo}">
            <span class="marcador">${icone(ICONES_HISTORICO[h.tipo])}</span>${escapar(h.descricao)}<small>${dataHora(h.dataHora)}</small></li>`).join("")}</ol></div>
      </div>`;
    acabamento(gaveta);
    if (!gaveta.open) gaveta.showModal();
    ligar(gaveta, c, aoMudar);
    if (foco === "proposta") {
      const alvo = $("[data-avaliacao] button, [data-proposta] input", gaveta);
      if (alvo) alvo.scrollIntoView({ block: "center" });
    }
  } catch (erro) {
    avisarErro(erro);
  }
}

function acoesDaEtapa(c, aberta) {
  if (!aberta) return "";
  const podeEncerrar = c.proximas.some((p) => p.etapa === "REPROVADO" || p.etapa === "DESISTIU");
  const encerrar = podeEncerrar ? `<button class="botao fantasma perigo" type="button" data-encerrar>Reprovar ou registrar desistência</button>` : "";
  switch (c.etapa) {
    case "INSCRITO":
      return `<div class="caixa-acao"><h4>Próximo passo: seleção</h4>
        <p class="suave">${c.recomendacao === "AGUARDAR" ? "Pretende acima do orçamento: fica no banco de talentos." : "Cabe no orçamento. Pode ser selecionado se houver vaga livre."}</p>
        <div class="acoes">${c.recomendacao !== "AGUARDAR" ? `<button class="botao primario" type="button" data-mover="SELECIONADO">Selecionar</button>` : ""}${encerrar}</div></div>`;
    case "SELECIONADO":
      return `<div class="caixa-acao"><h4>Ligação ${c.tentativasDeContato + 1} de 3</h4>
        <p class="suave">Na terceira sem resposta, o Crivo marca "sem contato" e chama o próximo da fila.</p>
        <div class="acoes"><button class="botao sucesso" type="button" data-contato="true">✓ Atendeu</button>
          <button class="botao perigo" type="button" data-contato="false">✕ Não atendeu</button>${encerrar}</div></div>`;
    case "ENTREVISTA": {
      const avaliacao = `<form class="caixa-acao" data-avaliacao novalidate><h4>${c.notaEntrevista ? "Reavaliar entrevista" : "Avaliar a entrevista"}</h4>
          <div class="escolha-estrelas" role="radiogroup" aria-label="Nota">${[1, 2, 3, 4, 5].map((n) =>
            `<button type="button" data-nota="${n}" aria-label="${n} de 5" class="${n <= (c.notaEntrevista || 0) ? "acesa" : ""}">★</button>`).join("")}</div>
          <label class="campo"><span>Parecer</span><textarea name="parecer" maxlength="500" placeholder="Pontos fortes, pontos de atenção...">${escapar(c.parecer || "")}</textarea></label>
          <p class="erro-form" hidden></p><div class="acoes">${encerrar}<button class="botao primario">Salvar avaliação</button></div></form>`;
      const proposta = c.notaEntrevista >= 3 ? `<form class="caixa-acao" data-proposta novalidate><h4>Enviar proposta</h4>
          <label class="campo"><span>Salário oferecido</span><input name="valor" inputmode="decimal"
            value="${Math.min(c.salarioPretendido, estado.vaga.salarioBase).toLocaleString("pt-BR", { minimumFractionDigits: 2 })}">
            <small>Até o orçamento de ${dinheiro(estado.vaga.salarioBase)}. Abaixo da pretensão (${dinheiro(c.salarioPretendido)}) fica registrado como contraproposta.</small></label>
          <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario">${icone("enviar")} Enviar proposta</button></div></form>`
        : c.notaEntrevista ? `<p class="suave">Nota ${c.notaEntrevista}/5: a proposta exige pelo menos 3.</p>` : "";
      return avaliacao + proposta;
    }
    case "PROPOSTA":
      return `<div class="caixa-acao"><h4>Proposta de ${dinheiroExato(c.salarioOfertado)} enviada</h4>
        <p class="suave">Aguardando a resposta há ${c.diasNaEtapa} dia(s).</p>
        <div class="acoes"><button class="botao primario" type="button" data-mover="CONTRATADO">${icone("check")} Aceitou: contratar</button>${encerrar}</div></div>`;
    default:
      return "";
  }
}

function ligar(gaveta, c, aoMudar) {
  const depois = async () => {
    await aoMudar();
    await abrirCandidato(c.id, aoMudar);
  };
  $(".fechar", gaveta).addEventListener("click", () => gaveta.close());
  $$("[data-aba]", gaveta).forEach((b) => b.addEventListener("click", () => {
    $$("[data-aba]", gaveta).forEach((x) => x.setAttribute("aria-selected", String(x === b)));
    $$("[data-painel]", gaveta).forEach((p) => { p.hidden = p.dataset.painel !== b.dataset.aba; });
  }));
  $$("[data-mover]", gaveta).forEach((b) => b.addEventListener("click", () => mover(c.id, b.dataset.mover, null, depois)));
  $$("[data-contato]", gaveta).forEach((b) => b.addEventListener("click", () => registrarContato(c.id, b.dataset.contato === "true", depois)));
  const enc = $("[data-encerrar]", gaveta);
  if (enc) enc.addEventListener("click", () => encerrarCandidato(c.id, async () => { await aoMudar(); }));

  const avaliacao = $("[data-avaliacao]", gaveta);
  if (avaliacao) {
    let nota = c.notaEntrevista || 0;
    $$("[data-nota]", avaliacao).forEach((b) => b.addEventListener("click", () => {
      nota = Number(b.dataset.nota);
      $$("[data-nota]", avaliacao).forEach((x) => x.classList.toggle("acesa", Number(x.dataset.nota) <= nota));
    }));
    avaliacao.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await api(`/api/candidatos/${c.id}/avaliacao`, { metodo: "POST", corpo: { nota: nota || null, parecer: avaliacao.parecer.value } });
        avisar(`Nota ${nota}/5 registrada.`, { titulo: "Avaliação salva", tipo: "sucesso" });
        await depois();
      } catch (erro) {
        erroNoFormulario(avaliacao, erro);
      }
    });
  }
  const proposta = $("[data-proposta]", gaveta);
  if (proposta) proposta.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const r = await api(`/api/candidatos/${c.id}/proposta`, { metodo: "POST", corpo: { valor: lerValor(proposta.valor.value) || null } });
      avisar(`${dinheiroExato(r.salarioOfertado)} para ${r.nome}.`, { titulo: "Proposta enviada", tipo: "sucesso" });
      await depois();
    } catch (erro) {
      erroNoFormulario(proposta, erro);
    }
  });
  const formNota = $("[data-anotar]", gaveta);
  formNota.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await api(`/api/candidatos/${c.id}/notas`, { metodo: "POST", corpo: { texto: formNota.texto.value } });
      avisar("Nota salva no histórico.", { tipo: "sucesso" });
      await abrirCandidato(c.id, aoMudar);
    } catch (erro) {
      avisarErro(erro);
    }
  });
}
