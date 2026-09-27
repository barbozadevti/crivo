// Utilitários de tela do Crivo: API, formatação, ícones, avatares, anel de compatibilidade, modal e avisos.

export const $ = (s, r = document) => r.querySelector(s);
export const $$ = (s, r = document) => [...r.querySelectorAll(s)];

const moedaBr = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 });
const moedaCentavos = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dataHoraBr = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "America/Sao_Paulo" });

export const dinheiro = (v) => moedaBr.format(Number(v)).replace(/ /g, " ");
export const dinheiroExato = (v) => moedaCentavos.format(Number(v)).replace(/ /g, " ");
export const dataHora = (iso) => dataHoraBr.format(new Date(iso)).replace(".", "");

export function escapar(t) {
  return String(t ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export function lerValor(texto) {
  let t = String(texto ?? "").trim().replace(/R\$|\s/g, "");
  if (t.includes(",")) t = t.replace(/\./g, "").replace(",", ".");
  return /^\d+(\.\d{1,2})?$/.test(t) ? Number(t) : NaN;
}

export function haQuanto(iso) {
  const dias = Math.floor((Date.now() - new Date(iso)) / 86400000);
  if (dias <= 0) return "hoje";
  if (dias === 1) return "ontem";
  return `há ${dias} dias`;
}

export async function api(caminho, { metodo = "GET", corpo } = {}) {
  const opcoes = { method: metodo, headers: { Accept: "application/json" } };
  if (corpo !== undefined) {
    opcoes.headers["Content-Type"] = "application/json";
    opcoes.body = JSON.stringify(corpo);
  }
  const r = await fetch(caminho, opcoes);
  if (r.status === 204) return null;
  const dados = await r.json().catch(() => null);
  if (!r.ok) {
    const erro = new Error(dados?.detail || "Não foi possível concluir a operação.");
    erro.titulo = dados?.title || "Erro";
    throw erro;
  }
  return dados;
}

// ---------- Visual ----------

const AREAS = { tecnologia: "tecnologia", dados: "dados", produto: "produto", administrativo: "administrativo", pessoas: "pessoas" };
export function classeDaArea(area) {
  const chave = String(area || "").toLowerCase().normalize("NFD").replace(/\p{M}/gu, "");
  return "area-" + (AREAS[chave] || "outra");
}

function matiz(texto) {
  let h = 0;
  for (const c of String(texto)) h = (h * 31 + c.charCodeAt(0)) % 360;
  return h;
}

export function iniciais(nome) {
  const p = String(nome || "?").trim().split(/\s+/);
  return (p[0][0] + (p.length > 1 ? p[p.length - 1][0] : "")).toUpperCase();
}

/** Avatar com as iniciais e uma cor estável por nome (a cor é aplicada depois, via CSSOM). */
export function avatar(nome, tamanho = "") {
  return `<span class="avatar ${tamanho}" data-matiz="${matiz(nome)}" aria-hidden="true">${escapar(iniciais(nome))}</span>`;
}

export function anel(pontuacao, tamanho = "") {
  const raio = 16;
  const volta = 2 * Math.PI * raio;
  const nivel = pontuacao >= 75 ? "alto" : pontuacao >= 50 ? "medio" : "baixo";
  return `<span class="anel ${tamanho} ${nivel}" title="Compatibilidade: ${pontuacao}%">
      <svg viewBox="0 0 40 40" aria-hidden="true"><circle class="trilho" cx="20" cy="20" r="${raio}"/>
        <circle class="valor" cx="20" cy="20" r="${raio}" stroke-dasharray="${(volta * pontuacao) / 100} ${volta}"/></svg>
      <b>${pontuacao}</b></span>`;
}

export function estrelas(nota) {
  if (!nota) return "";
  return `<span class="estrelas" title="Entrevista: ${nota}/5">${"★".repeat(nota)}<span class="apagada">${"★".repeat(5 - nota)}</span></span>`;
}

/** Aplica o que depende de CSSOM: cor dos avatares e larguras das barras (sem estilo inline no HTML). */
export function acabamento(raiz = document) {
  $$("[data-matiz]", raiz).forEach((el) => el.style.setProperty("--h", el.dataset.matiz));
  $$("[data-largura]", raiz).forEach((el) => requestAnimationFrame(() => { el.style.width = el.dataset.largura + "%"; }));
}

const caminhos = {
  painel: '<rect x="3" y="3" width="7" height="9" rx="2"/><rect x="14" y="3" width="7" height="5" rx="2"/><rect x="14" y="12" width="7" height="9" rx="2"/><rect x="3" y="16" width="7" height="5" rx="2"/>',
  vagas: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 13h18"/>',
  carreiras: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
  api: '<path d="m8 8-5 4 5 4M16 8l5 4-5 4M14 4l-4 16"/>',
  telefone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>',
  estrela: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2-5.5-2.9-5.5 2.9 1-6.2L3 9.6l6.2-.9z"/>',
  enviar: '<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>',
  relogio: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  filtro: '<path d="M3 5h18l-7 8v6l-4 2v-8z"/>',
  usuarios: '<circle cx="9" cy="8" r="4"/><path d="M2 21a7 7 0 0 1 14 0M16 4a4 4 0 0 1 0 8M22 21a7 7 0 0 0-5-6.7"/>',
  check: '<path d="m5 12 5 5 9-10"/>',
  alvo: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  grafico: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  nota: '<path d="M4 4h16v12l-4 4H4z"/><path d="M16 20v-4h4M8 9h8M8 13h5"/>',
  seta: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  mais: '<path d="M12 5v14M5 12h14"/>',
  play: '<path d="M7 4v16l13-8z"/>',
  mapa: '<path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
  predio: '<path d="M4 21V5l8-3v19M12 8h8v13M8 8v.01M8 12v.01M8 16v.01M16 12v.01M16 16v.01"/>',
  linkedin: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M8 10v7M8 7v.01M12 17v-4a2 2 0 0 1 4 0v4M12 10v7"/>',
  email: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/>',
};

export function icone(nome) {
  return `<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${caminhos[nome] || ""}</svg>`;
}

// ---------- Avisos e modal ----------

export function avisar(mensagem, { titulo, tipo = "", duracao = 5000 } = {}) {
  const el = document.createElement("div");
  el.className = "aviso " + tipo;
  el.innerHTML = (titulo ? `<strong>${escapar(titulo)}</strong>` : "") + escapar(mensagem);
  $("#avisos").append(el);
  setTimeout(() => el.remove(), duracao);
}

export const avisarErro = (e) => avisar(e.message, { titulo: e.titulo || "Erro", tipo: "erro", duracao: 7000 });

export function abrirModal(titulo, html, subtitulo = "") {
  const modal = $("#modal");
  modal.innerHTML = `<div class="modal-corpo"><div class="modal-topo"><div><h2 id="modal-titulo">${escapar(titulo)}</h2>
      ${subtitulo ? `<p class="suave">${subtitulo}</p>` : ""}</div>
      <button class="fechar" type="button" aria-label="Fechar">×</button></div><div class="conteudo">${html}</div></div>`;
  $(".fechar", modal).addEventListener("click", () => modal.close());
  if (!modal.open) modal.showModal();
  acabamento(modal);
  return $(".conteudo", modal);
}

export const fecharModal = () => $("#modal").open && $("#modal").close();

export function erroNoFormulario(form, erro) {
  const alvo = $(".erro-form", form);
  alvo.textContent = erro.message;
  alvo.hidden = false;
}

export const NOMES_ETAPAS = {
  INSCRITO: "Inscritos", SELECIONADO: "Selecionados", ENTREVISTA: "Entrevista", PROPOSTA: "Proposta", CONTRATADO: "Contratados",
  SEM_CONTATO: "Sem contato", REPROVADO: "Reprovados", DESISTIU: "Desistências",
};
