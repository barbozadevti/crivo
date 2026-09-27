// Crivo: lista de vagas, quadro Kanban por vaga, ficha do candidato, simulação e cadastros.

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dataHora = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" });
const dinheiro = (v) => moeda.format(Number(v)).replace(/ /g, " ");

const COLUNAS = ["INSCRITO", "SELECIONADO", "ENTREVISTA", "PROPOSTA", "CONTRATADO"];
const ENCERRADAS = ["SEM_CONTATO", "REPROVADO", "DESISTIU"];
const NOMES = {
  INSCRITO: "Inscritos", SELECIONADO: "Selecionados", ENTREVISTA: "Entrevista", PROPOSTA: "Proposta", CONTRATADO: "Contratados",
  SEM_CONTATO: "Sem contato", REPROVADO: "Reprovados", DESISTIU: "Desistências",
};

function escapar(t) {
  return String(t ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

async function api(caminho, { metodo = "GET", corpo } = {}) {
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

function avisar(mensagem, { titulo, tipo = "", duracao = 5000 } = {}) {
  const el = document.createElement("div");
  el.className = "aviso " + tipo;
  el.innerHTML = (titulo ? `<strong>${escapar(titulo)}</strong>` : "") + escapar(mensagem);
  $("#avisos").append(el);
  setTimeout(() => el.remove(), duracao);
}
const avisarErro = (e) => avisar(e.message, { titulo: e.titulo || "Erro", tipo: "erro", duracao: 7000 });

function lerValor(texto) {
  let t = String(texto ?? "").trim().replace(/R\$|\s/g, "");
  if (t.includes(",")) t = t.replace(/\./g, "").replace(",", ".");
  return /^\d+(\.\d{1,2})?$/.test(t) ? Number(t) : NaN;
}

function abrirModal(titulo, html, subtitulo = "") {
  const modal = $("#modal");
  modal.innerHTML = `<div class="modal-corpo"><div class="modal-topo"><div><h2 id="modal-titulo">${escapar(titulo)}</h2>
      ${subtitulo ? `<p class="suave">${subtitulo}</p>` : ""}</div>
      <button class="fechar" type="button" aria-label="Fechar">×</button></div><div class="conteudo">${html}</div></div>`;
  $(".fechar", modal).addEventListener("click", () => modal.close());
  if (!modal.open) modal.showModal();
  return $(".conteudo", modal);
}
const fecharModal = () => $("#modal").open && $("#modal").close();

// ---------- Lista de vagas ----------

function funilMini(v) {
  if (!v.total) return `<div class="funil-mini"></div>`;
  return `<div class="funil-mini" title="Distribuição dos candidatos por etapa">${[...COLUNAS, ...ENCERRADAS]
    .filter((e) => v.porEtapa[e]).map((e) => `<span class="e-${e}" data-largura="${(v.porEtapa[e] / v.total) * 100}"
      title="${NOMES[e]}: ${v.porEtapa[e]}"></span>`).join("")}</div>`;
}

function aplicarLarguras(raiz) {
  $$("[data-largura]", raiz).forEach((el) => { el.style.width = el.dataset.largura + "%"; });
}

async function telaVagas(principal) {
  const vagas = await api("/api/vagas");
  principal.innerHTML = `<div class="cabecalho"><div><h1>Vagas</h1>
      <p>${vagas.filter((v) => v.status === "ABERTA").length} abertas · ${vagas.reduce((s, v) => s + v.total, 0)} candidatos no funil</p></div>
      <button class="botao primario" type="button" data-nova-vaga>+ Nova vaga</button></div>
    <div class="grade-vagas">${vagas.map((v) => `<a class="cartao-vaga" href="#/vagas/${v.id}">
        <div class="linha"><span class="chip">${escapar(v.area)}</span><span class="chip ${v.status === "ABERTA" ? "aberta" : "encerrada"}">${v.status === "ABERTA" ? "Aberta" : "Encerrada"}</span></div>
        <div><h2>${escapar(v.titulo)}</h2><p class="suave">Até ${dinheiro(v.salarioBase)} · ${v.quantidade} ${v.quantidade > 1 ? "vagas" : "vaga"}</p></div>
        ${funilMini(v)}
        <div class="numeros"><span><strong>${v.total}</strong>candidatos</span><span><strong>${v.ocupadas}/${v.quantidade}</strong>vagas ocupadas</span>
          <span><strong>${v.contratados}</strong>contratados</span></div>
        <div class="progresso" title="Contratações"><span data-largura="${(v.contratados / v.quantidade) * 100}"></span></div>
      </a>`).join("")}</div>`;
  aplicarLarguras(principal);
  $("[data-nova-vaga]", principal).addEventListener("click", novaVaga);
}

function novaVaga() {
  const corpo = abrirModal("Nova vaga", `<form class="formulario" novalidate>
      <label class="campo"><span>Título</span><input name="titulo" maxlength="80" placeholder="Ex.: Analista de Dados Pleno"></label>
      <div class="duas-colunas"><label class="campo"><span>Área</span><input name="area" maxlength="40" placeholder="Tecnologia"></label>
        <label class="campo"><span>Quantidade de vagas</span><input name="quantidade" type="number" min="1" max="50" value="1"></label></div>
      <label class="campo"><span>Salário base (orçamento)</span><input name="salario" inputmode="decimal" placeholder="4.500,00">
        <small>Quem pretender até este valor passa na triagem.</small></label>
      <label class="campo"><span>Descrição (opcional)</span><input name="descricao" maxlength="500"></label>
      <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario">Criar vaga</button></div></form>`);
  const f = $("form", corpo);
  f.titulo.focus();
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const vaga = await api("/api/vagas", { metodo: "POST", corpo: { titulo: f.titulo.value, area: f.area.value,
        descricao: f.descricao.value, salarioBase: lerValor(f.salario.value) || null, quantidade: Number(f.quantidade.value) } });
      fecharModal();
      location.hash = "#/vagas/" + vaga.id;
    } catch (erro) {
      $(".erro-form", f).textContent = erro.message;
      $(".erro-form", f).hidden = false;
    }
  });
}

// ---------- Vaga e quadro ----------

let vagaAtual = null;
let candidatosAtuais = [];

async function telaVaga(principal, id) {
  const [vaga, candidatos] = await Promise.all([api("/api/vagas/" + id), api(`/api/vagas/${id}/candidatos`)]);
  vagaAtual = vaga;
  candidatosAtuais = candidatos;
  const aberta = vaga.status === "ABERTA";
  const encerrados = candidatos.filter((c) => ENCERRADAS.includes(c.etapa));
  const maximo = Math.max(1, vaga.total);
  principal.innerHTML = `<div class="painel-vaga">
    <div class="cabecalho"><div><p class="suave"><a href="#/">Vagas</a> / ${escapar(vaga.area)}</p>
        <h1>${escapar(vaga.titulo)} <span class="chip ${aberta ? "aberta" : "encerrada"}">${aberta ? "Aberta" : "Encerrada"}</span></h1>
        <p>Orçamento de ${dinheiro(vaga.salarioBase)} · ${vaga.quantidade} ${vaga.quantidade > 1 ? "vagas" : "vaga"}${vaga.descricao ? " · " + escapar(vaga.descricao) : ""}</p></div>
      <div class="acoes">${aberta ? `
        <button class="botao" type="button" data-acao="importar">Importar CSV</button>
        <button class="botao" type="button" data-acao="candidato">+ Candidato</button>
        <button class="botao" type="button" data-acao="simular">▶ Simular processo</button>
        <button class="botao primario" type="button" data-acao="selecionar" ${vaga.livres ? "" : "disabled title=\"Todas as vagas estão ocupadas\""}>Selecionar (${vaga.livres} ${vaga.livres === 1 ? "vaga livre" : "vagas livres"})</button>
        <button class="botao fantasma perigo" type="button" data-acao="encerrar">Encerrar</button>` : ""}</div></div>
    <div class="indicadores">
      <div class="indicador"><small>Candidatos</small><strong>${vaga.total}</strong></div>
      <div class="indicador"><small>Vagas ocupadas</small><strong>${vaga.ocupadas}/${vaga.quantidade}</strong>
        <div class="progresso"><span data-largura="${(vaga.ocupadas / vaga.quantidade) * 100}"></span></div></div>
      <div class="indicador"><small>Contratados</small><strong>${vaga.contratados}</strong></div>
      <div class="indicador"><small>Pedem acima do orçamento</small><strong>${vaga.acimaDoOrcamento}</strong></div>
    </div>
    <div class="funil"><h3>Funil</h3>${[...COLUNAS, ...ENCERRADAS].map((e) => `<div class="barra-funil e-${e}">
        <span>${NOMES[e]}</span><span class="trilho"><span data-largura="${(vaga.porEtapa[e] / maximo) * 100}"></span></span><b>${vaga.porEtapa[e]}</b></div>`).join("")}</div>
    <div class="quadro">${COLUNAS.map((e) => coluna(e, candidatos.filter((c) => c.etapa === e), aberta)).join("")}
      <section class="coluna" data-coluna="ENCERRADOS"><div class="coluna-topo"><span><span class="ponto e-REPROVADO"></span>Encerrados</span>
        <span class="contagem">${encerrados.length}</span></div>
        <div class="encerrados">${encerrados.length ? encerrados.map(cartao).join("") : `<p class="vazio-coluna">Solte aqui para reprovar ou registrar desistência</p>`}</div></section>
    </div></div>`;
  aplicarLarguras(principal);
  ligarQuadro(principal, aberta);
  $$("[data-acao]", principal).forEach((b) => b.addEventListener("click", () => acoes[b.dataset.acao]()));
}

function coluna(etapa, lista, aberta) {
  return `<section class="coluna e-${etapa}" data-coluna="${etapa}"><div class="coluna-topo"><span><span class="ponto"></span>${NOMES[etapa]}</span>
      <span class="contagem">${lista.length}</span></div>
      ${lista.length ? lista.map(cartao).join("") : `<p class="vazio-coluna">${aberta ? "Arraste um candidato para cá" : "Ninguém nesta etapa"}</p>`}</section>`;
}

function cartao(c) {
  const tentativas = c.etapa === "SELECIONADO" ? `<div class="tentativas" title="Tentativas de contato sem sucesso">
      ${[0, 1, 2].map((i) => `<i class="${i < c.tentativasDeContato ? "feita" : ""}"></i>`).join("")}
      ${c.tentativasDeContato ? `${c.tentativasDeContato}/3 sem resposta` : "nenhuma ligação ainda"}</div>
      <div class="contato-rapido"><button class="botao pequeno sucesso" data-contato="true" data-id="${c.id}">✓ Atendeu</button>
        <button class="botao pequeno perigo" data-contato="false" data-id="${c.id}">✕ Não atendeu</button></div>` : "";
  return `<article class="cartao e-${c.etapa}" draggable="${c.proximas.length > 0}" data-id="${c.id}" tabindex="0">
      <span class="nome">${escapar(c.nome)}</span>
      <span class="detalhe"><span>${dinheiro(c.salarioPretendido)}</span>
        ${ENCERRADAS.includes(c.etapa) ? `<span class="selo AGUARDAR">${NOMES[c.etapa]}</span>` : `<span class="selo ${c.recomendacao}">${c.recomendacao === "LIGAR" ? "No orçamento" : c.recomendacao === "CONTRAPROPOSTA" ? "No limite" : "Acima"}</span>`}</span>
      ${tentativas}</article>`;
}

function ligarQuadro(raiz, aberta) {
  $$(".cartao", raiz).forEach((el) => {
    el.addEventListener("click", (e) => {
      if (e.target.closest("[data-contato]")) return;
      fichaDoCandidato(el.dataset.id);
    });
    el.addEventListener("keydown", (e) => { if (e.key === "Enter") fichaDoCandidato(el.dataset.id); });
    if (!aberta) return;
    el.addEventListener("dragstart", (e) => {
      const c = candidatosAtuais.find((x) => String(x.id) === el.dataset.id);
      const permitidas = new Set(c.proximas.map((p) => p.etapa));
      e.dataTransfer.setData("text/plain", el.dataset.id);
      e.dataTransfer.effectAllowed = "move";
      el.classList.add("arrastando");
      $$(".coluna", raiz).forEach((col) => {
        const destino = col.dataset.coluna;
        const pode = destino === "ENCERRADOS" ? permitidas.has("REPROVADO") || permitidas.has("DESISTIU") : permitidas.has(destino);
        col.classList.toggle("pode-soltar", pode);
        col.classList.toggle("nao-pode", !pode && destino !== c.etapa);
      });
    });
    el.addEventListener("dragend", () => {
      el.classList.remove("arrastando");
      $$(".coluna", raiz).forEach((col) => col.classList.remove("pode-soltar", "nao-pode", "alvo"));
    });
  });
  $$(".coluna", raiz).forEach((col) => {
    col.addEventListener("dragover", (e) => {
      if (col.classList.contains("pode-soltar")) {
        e.preventDefault();
        col.classList.add("alvo");
      }
    });
    col.addEventListener("dragleave", () => col.classList.remove("alvo"));
    col.addEventListener("drop", async (e) => {
      e.preventDefault();
      const id = e.dataTransfer.getData("text/plain");
      if (!col.classList.contains("pode-soltar")) return;
      if (col.dataset.coluna === "ENCERRADOS") return encerrarCandidato(id);
      await mover(id, col.dataset.coluna);
    });
  });
  $$("[data-contato]", raiz).forEach((b) => b.addEventListener("click", (e) => {
    e.stopPropagation();
    registrarContato(b.dataset.id, b.dataset.contato === "true");
  }));
}

async function recarregar() {
  await rotear();
}

async function mover(id, etapa, motivo) {
  try {
    const c = await api(`/api/candidatos/${id}/etapa`, { metodo: "POST", corpo: { etapa, motivo } });
    avisar(`${c.nome} → ${NOMES[etapa]}`, { tipo: "sucesso" });
    fecharModal();
    await recarregar();
  } catch (erro) {
    avisarErro(erro);
  }
}

async function registrarContato(id, atendeu) {
  try {
    const r = await api(`/api/candidatos/${id}/contatos`, { metodo: "POST", corpo: { atendeu } });
    avisar(r.mensagem, { titulo: { ATENDEU: "Contato feito", NAO_ATENDEU: "Não atendeu", SEM_CONTATO: "Sem contato" }[r.resultado],
      tipo: r.resultado === "ATENDEU" ? "sucesso" : r.resultado === "SEM_CONTATO" ? "erro" : "", duracao: 7000 });
    fecharModal();
    await recarregar();
  } catch (erro) {
    avisarErro(erro);
  }
}

function encerrarCandidato(id) {
  const c = candidatosAtuais.find((x) => String(x.id) === String(id));
  const opcoes = c.proximas.filter((p) => p.etapa === "REPROVADO" || p.etapa === "DESISTIU");
  const corpo = abrirModal("Encerrar o processo de " + c.nome, `<form class="formulario" novalidate>
      <label class="campo"><span>Motivo</span><select name="etapa">${opcoes.map((o) => `<option value="${o.etapa}">${o.etapa === "REPROVADO" ? "Reprovado pelo processo" : "Desistiu"}</option>`).join("")}</select></label>
      <label class="campo"><span>Observação (fica no histórico)</span><input name="motivo" maxlength="120" placeholder="Ex.: aceitou outra proposta"></label>
      <div class="acoes"><button class="botao primario">Confirmar</button></div></form>`,
    c.etapa === "INSCRITO" ? "" : "A vaga que ele ocupava é liberada e o próximo da fila que cabe no orçamento é chamado.");
  $("form", corpo).addEventListener("submit", (e) => {
    e.preventDefault();
    mover(id, e.target.etapa.value, e.target.motivo.value);
  });
}

async function fichaDoCandidato(id) {
  try {
    const { candidato: c, historico } = await api("/api/candidatos/" + id);
    const aberta = vagaAtual?.status === "ABERTA";
    const botoes = !aberta ? "" : c.proximas.map((p) => ["REPROVADO", "DESISTIU"].includes(p.etapa)
      ? "" : `<button class="botao ${p.etapa === "CONTRATADO" ? "primario" : ""}" data-mover="${p.etapa}">Mover para ${p.nome}</button>`).join("")
      + (c.proximas.some((p) => ["REPROVADO", "DESISTIU"].includes(p.etapa)) ? `<button class="botao fantasma perigo" data-encerrar>Encerrar processo…</button>` : "");
    const contato = aberta && c.etapa === "SELECIONADO"
      ? `<div class="acoes"><span class="suave">Ligação ${c.tentativasDeContato + 1} de 3:</span>
          <button class="botao sucesso" data-contato="true">✓ Atendeu</button><button class="botao perigo" data-contato="false">✕ Não atendeu</button></div>` : "";
    const corpo = abrirModal(c.nome, `<div class="formulario">
        <div class="ficha">
          <div><small>Etapa</small>${NOMES[c.etapa]}</div>
          <div><small>Triagem</small><span class="selo ${c.recomendacao}">${escapar(c.recomendacaoTexto)}</span></div>
          <div><small>Pretensão</small>${dinheiro(c.salarioPretendido)}</div>
          <div><small>Orçamento da vaga</small>${vagaAtual ? dinheiro(vagaAtual.salarioBase) : "—"}</div>
          <div><small>E-mail</small>${escapar(c.email)}</div>
          <div><small>Telefone</small>${escapar(c.telefone || "—")}</div>
        </div>
        ${contato}
        ${botoes ? `<div class="acoes">${botoes}</div>` : ""}
        <h3>Histórico</h3>
        <ol class="linha-do-tempo">${historico.map((h) => `<li>${escapar(h.descricao)}<small>${dataHora.format(new Date(h.dataHora))}</small></li>`).join("")}</ol>
      </div>`, `Inscrito em ${dataHora.format(new Date(c.inscritoEm))}`);
    $$("[data-mover]", corpo).forEach((b) => b.addEventListener("click", () => mover(c.id, b.dataset.mover)));
    $$("[data-contato]", corpo).forEach((b) => b.addEventListener("click", () => registrarContato(c.id, b.dataset.contato === "true")));
    const enc = $("[data-encerrar]", corpo);
    if (enc) enc.addEventListener("click", () => encerrarCandidato(c.id));
  } catch (erro) {
    avisarErro(erro);
  }
}

const acoes = {
  async selecionar() {
    try {
      const lista = await api(`/api/vagas/${vagaAtual.id}/selecao`, { metodo: "POST" });
      avisar(lista.map((c) => c.nome).join(", "), { titulo: `${lista.length} selecionado(s), em ordem de inscrição`, tipo: "sucesso", duracao: 7000 });
      await recarregar();
    } catch (erro) {
      avisarErro(erro);
    }
  },
  simular() {
    const corpo = abrirModal("Simular o processo seletivo", `<form class="formulario" novalidate>
        <p class="suave">Seleciona quem cabe no orçamento e sorteia o resto: cada ligação tem 1 chance em 3 de ser atendida (como nas aulas),
          70% passam na entrevista e 80% aceitam a proposta. Com a mesma semente, o resultado é sempre igual.</p>
        <label class="campo"><span>Semente</span><input name="semente" type="number" value="42"></label>
        <div class="acoes"><button class="botao primario">▶ Simular</button></div></form><div data-registro></div>`);
    $("form", corpo).addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const s = await api(`/api/vagas/${vagaAtual.id}/simulacao?semente=${encodeURIComponent(e.target.semente.value)}`, { metodo: "POST" });
        const classe = (p) => /contratad|Conseguimos|passou/.test(p) ? "ok" : /Não conseguimos|não passou|recusou|não atendeu/.test(p) ? "ruim" : "info";
        $("[data-registro]", corpo).innerHTML = `<div class="registro">${s.passos.map((p) => `<span class="${classe(p)}">› ${escapar(p)}</span>`).join("")}</div>`;
        $("form", corpo).hidden = true;
        await telaVaga($("#principal"), vagaAtual.id);
      } catch (erro) {
        avisarErro(erro);
      }
    });
  },
  candidato() {
    const corpo = abrirModal("Novo candidato", `<form class="formulario" novalidate>
        <label class="campo"><span>Nome</span><input name="nome" maxlength="80"></label>
        <div class="duas-colunas"><label class="campo"><span>E-mail</span><input name="email" type="email"></label>
          <label class="campo"><span>Telefone (opcional)</span><input name="telefone" inputmode="tel"></label></div>
        <label class="campo"><span>Salário pretendido</span><input name="salario" inputmode="decimal" placeholder="0,00">
          <small>Orçamento da vaga: ${dinheiro(vagaAtual.salarioBase)}. A triagem é feita na hora.</small></label>
        <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario">Inscrever</button></div></form>`);
    const f = $("form", corpo);
    f.nome.focus();
    f.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const c = await api(`/api/vagas/${vagaAtual.id}/candidatos`, { metodo: "POST", corpo: { nome: f.nome.value, email: f.email.value,
          telefone: f.telefone.value, salarioPretendido: lerValor(f.salario.value) || null } });
        fecharModal();
        avisar(`Triagem: ${c.recomendacaoTexto.toLowerCase()}.`, { titulo: c.nome + " inscrito", tipo: "sucesso" });
        await recarregar();
      } catch (erro) {
        $(".erro-form", f).textContent = erro.message;
        $(".erro-form", f).hidden = false;
      }
    });
  },
  importar() {
    const exemplo = "nome;email;telefone;salario\nLeticia Moraes;leticia@email.com;(27) 99811-2233;3.200,00\nRodrigo Sales;rodrigo@email.com;;3.900,00";
    const corpo = abrirModal("Importar candidatos", `<form class="formulario" novalidate>
        <label class="campo"><span>Cole as linhas da planilha</span><textarea name="texto" spellcheck="false">${exemplo}</textarea>
          <small>Uma linha por candidato: nome;e-mail;telefone;salário (vírgula também serve). A primeira linha pode ser o cabeçalho.</small></label>
        <div data-resultado></div><div class="acoes"><button class="botao primario">Importar</button></div></form>`);
    const f = $("form", corpo);
    f.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const r = await api(`/api/vagas/${vagaAtual.id}/importacao`, { metodo: "POST", corpo: { texto: f.texto.value } });
        $("[data-resultado]", corpo).innerHTML = `<p><strong>${r.importados} importado(s).</strong></p>
          ${r.erros.length ? `<ul class="erro-form">${r.erros.map((x) => `<li>${escapar(x)}</li>`).join("")}</ul>` : ""}`;
        await telaVaga($("#principal"), vagaAtual.id);
      } catch (erro) {
        avisarErro(erro);
      }
    });
  },
  async encerrar() {
    const corpo = abrirModal("Encerrar vaga", `<div class="formulario"><p>A vaga deixa de aceitar inscrições e mudanças. Os candidatos continuam no histórico.</p>
      <div class="acoes"><button class="botao primario" data-sim>Encerrar vaga</button></div></div>`);
    $("[data-sim]", corpo).addEventListener("click", async () => {
      try {
        await api(`/api/vagas/${vagaAtual.id}/encerramento`, { metodo: "POST" });
        fecharModal();
        await recarregar();
      } catch (erro) {
        avisarErro(erro);
      }
    });
  },
};

// ---------- Rotas ----------

async function rotear() {
  const principal = $("#principal");
  const partes = location.hash.replace(/^#\/?/, "").split("/");
  try {
    if (partes[0] === "vagas" && partes[1]) {
      await telaVaga(principal, partes[1]);
    } else {
      vagaAtual = null;
      await telaVagas(principal);
    }
  } catch (erro) {
    principal.innerHTML = `<p class="carregando">${escapar(erro.message)} <a href="#/">Voltar às vagas</a></p>`;
  }
}

window.addEventListener("hashchange", () => { rotear(); window.scrollTo({ top: 0 }); });
$("#reiniciar").addEventListener("click", async () => {
  try {
    await api("/api/demonstracao/reinicio", { metodo: "POST" });
    avisar("Vagas e candidatos de exemplo recarregados.", { titulo: "Demonstração reiniciada", tipo: "sucesso" });
    location.hash = "#/";
    await rotear();
  } catch (erro) {
    avisarErro(erro);
  }
});
$("#principal").innerHTML = `<p class="carregando">Carregando…</p>`;
rotear();
