// Visão geral do recrutador: boas-vindas, indicadores, o que precisa de atenção, funil, origem e atividade.
import { $, NOMES_ETAPAS, acabamento, api, avatar, dataHora, escapar, icone } from "./ui.js";

const ICONES_ATENCAO = {
  SELECIONAR: "filtro", LIGAR: "telefone", TENTAR_DE_NOVO: "telefone", AVALIAR: "estrela",
  ENVIAR_PROPOSTA: "enviar", REVER_AVALIACAO: "alvo", COBRAR_PROPOSTA: "relogio",
};
const ORIGENS = {
  CARREIRAS: ["Página de carreiras", "#f97316"], LINKEDIN: ["LinkedIn", "#0a66c2"], INDICACAO: ["Indicação", "#10b981"],
  IMPORTACAO: ["Importação", "#8b5cf6"], MANUAL: ["Cadastro manual", "#94a3b8"],
};

function saudacao() {
  const h = Number(new Intl.DateTimeFormat("pt-BR", { hour: "numeric", hourCycle: "h23", timeZone: "America/Sao_Paulo" }).format(new Date()));
  return h < 5 ? "Boa noite" : h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";
}

function rosca(origens) {
  const entradas = Object.entries(origens).filter(([, n]) => n > 0);
  const total = entradas.reduce((s, [, n]) => s + n, 0) || 1;
  const raio = 52;
  const volta = 2 * Math.PI * raio;
  let acumulado = 0;
  const fatias = entradas.map(([origem, n]) => {
    const tamanho = (n / total) * volta;
    const fatia = `<circle cx="75" cy="75" r="${raio}" fill="none" stroke="${ORIGENS[origem][1]}" stroke-width="22"
        stroke-dasharray="${tamanho} ${volta}" stroke-dashoffset="${-acumulado}" transform="rotate(-90 75 75)"><title>${ORIGENS[origem][0]}: ${n}</title></circle>`;
    acumulado += tamanho;
    return fatia;
  });
  return `<div class="rosca"><svg viewBox="0 0 150 150" role="img" aria-label="Origem dos candidatos">${fatias.join("")}
      <text x="75" y="72" text-anchor="middle" font-size="26" font-weight="800" fill="currentColor">${total}</text>
      <text x="75" y="92" text-anchor="middle" font-size="11" fill="currentColor" opacity=".6">candidatos</text></svg>
    <div class="legenda">${entradas.map(([o, n]) => `<span><i data-cor="${ORIGENS[o][1]}"></i>${ORIGENS[o][0]} <b>${n}</b>
      <span class="suave">${Math.round((n / total) * 100)}%</span></span>`).join("")}</div></div>`;
}

export async function telaPainel(principal) {
  const p = await api("/api/painel");
  const maximo = Math.max(1, ...Object.values(p.funil));
  const pendencias = p.atencao.length;
  principal.innerHTML = `
    <section class="boas-vindas">
      <h1>${saudacao()}, Juliana</h1>
      <p>${pendencias ? `Você tem <strong>${pendencias} ${pendencias === 1 ? "pendência" : "pendências"}</strong> hoje em ${p.vagasAbertas} vagas abertas.`
        : "Tudo em dia por aqui."} O Crivo cuida da triagem, da fila de suplentes e do encerramento das vagas; você cuida das pessoas.</p>
      <div class="acoes"><a class="botao branco" href="#/vagas">${icone("vagas")} Ver vagas</a>
        <a class="botao vidro" href="#/carreiras" target="_blank" rel="noopener">${icone("carreiras")} Página de carreiras</a></div>
    </section>
    <div class="kpis">
      <div class="kpi"><span class="icone i-indigo">${icone("vagas")}</span><small>Vagas abertas</small><strong>${p.vagasAbertas}</strong></div>
      <div class="kpi"><span class="icone i-ciano">${icone("usuarios")}</span><small>Candidatos em andamento</small><strong>${p.candidatosAtivos}</strong></div>
      <div class="kpi"><span class="icone i-verde">${icone("check")}</span><small>Contratações (30 dias)</small><strong>${p.contratacoesNoMes}</strong></div>
      <div class="kpi"><span class="icone i-ambar">${icone("relogio")}</span><small>Tempo até contratar</small>
        <strong>${p.diasAteContratar == null ? "—" : `${String(p.diasAteContratar).replace(".", ",")} dias`}</strong><em>da inscrição ao sim</em></div>
      <div class="kpi"><span class="icone i-rosa">${icone("grafico")}</span><small>Conversão</small><strong>${p.conversao}%</strong><em>inscritos que foram contratados</em></div>
    </div>
    <div class="painel-grade">
      <div class="coluna-painel">
        <section class="bloco"><div class="bloco-topo"><h2>Precisa de atenção hoje</h2><span class="suave">${pendencias} ${pendencias === 1 ? "item" : "itens"}</span></div>
          <div class="atencao">${pendencias ? p.atencao.map((a) => `<a class="item-atencao t-${a.tipo}"
              href="#/vagas/${a.vagaId}${a.candidatoId ? "?candidato=" + a.candidatoId : ""}">
              <span class="icone">${icone(ICONES_ATENCAO[a.tipo])}</span>
              <span><strong>${escapar(a.titulo)}</strong><small>${escapar(a.detalhe)} · ${escapar(a.vaga)}</small></span>
              ${icone("seta")}</a>`).join("") : `<p class="suave">Nenhuma pendência. 🎉</p>`}</div>
        </section>
        <section class="bloco"><div class="bloco-topo"><h2>Atividade recente</h2></div>
          <ul class="linha-tempo">${p.atividade.map((a) => `<li>${avatar(a.candidato, "pequeno")}
            <span><a href="#/vagas/${a.vagaId}?candidato=${a.candidatoId}"><strong>${escapar(a.candidato)}</strong></a> · ${escapar(a.descricao)}
            <small>${escapar(a.vaga)} · ${dataHora(a.dataHora)}</small></span></li>`).join("")}</ul>
        </section>
      </div>
      <div class="coluna-painel">
        <section class="bloco"><div class="bloco-topo"><h2>Funil de todas as vagas</h2></div>
          <div class="barras">${Object.entries(p.funil).map(([e, n]) => `<div class="barra-h e-${e}"><span>${NOMES_ETAPAS[e]}</span>
            <span class="trilho"><span data-largura="${(n / maximo) * 100}"></span></span><b>${n}</b></div>`).join("")}</div>
        </section>
        <section class="bloco"><div class="bloco-topo"><h2>De onde vêm os candidatos</h2></div>${rosca(p.origens)}</section>
      </div>
    </div>`;
  principal.querySelectorAll("[data-cor]").forEach((i) => { i.style.background = i.dataset.cor; });
  acabamento(principal);
}

export { $ };
