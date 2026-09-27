// Página da vaga: capa colorida, indicadores e quadro Kanban com arrastar e soltar validado pela máquina de estados.
import {
  $, $$, NOMES_ETAPAS, abrirModal, acabamento, anel, api, avatar, avisar, avisarErro, classeDaArea, dinheiro,
  erroNoFormulario, escapar, estrelas, fecharModal, icone, lerValor,
} from "./ui.js";
import { abrirCandidato } from "./candidato.js";

const COLUNAS = ["INSCRITO", "SELECIONADO", "ENTREVISTA", "PROPOSTA", "CONTRATADO"];
const ENCERRADAS = ["SEM_CONTATO", "REPROVADO", "DESISTIU"];

export const estado = { vaga: null, candidatos: [] };

export async function telaVaga(principal, id, candidatoParaAbrir) {
  const [vaga, candidatos] = await Promise.all([api("/api/vagas/" + id), api(`/api/vagas/${id}/candidatos`)]);
  estado.vaga = vaga;
  estado.candidatos = candidatos;
  const aberta = vaga.status === "ABERTA";
  const encerrados = candidatos.filter((c) => ENCERRADAS.includes(c.etapa));
  principal.innerHTML = `
    <section class="capa-vaga degrade ${classeDaArea(vaga.area)}">
      <p class="trilha"><a href="#/vagas">Vagas</a> / ${escapar(vaga.area)}</p>
      <h1>${escapar(vaga.titulo)} <span class="chip vidro">${aberta ? "Aberta" : "Encerrada"}</span></h1>
      <div class="meta"><span class="chip vidro">${icone("predio")} ${escapar(vaga.modeloNome)}</span>
        <span class="chip vidro">${icone("mapa")} ${escapar(vaga.local || "—")}</span>
        <span class="chip vidro">Orçamento ${dinheiro(vaga.salarioBase)}</span>
        <span class="chip vidro">${vaga.quantidade} ${vaga.quantidade > 1 ? "vagas" : "vaga"}</span></div>
      ${vaga.descricao ? `<p class="descricao">${escapar(vaga.descricao)}</p>` : ""}
      ${vaga.requisitos.length ? `<div class="chips meta">${vaga.requisitos.map((r) => `<span class="chip vidro">✓ ${escapar(r)}</span>`).join("")}</div>` : ""}
      ${aberta ? `<div class="acoes">
        <button class="botao branco" type="button" data-acao="selecionar" ${vaga.livres ? "" : "disabled"}>${icone("filtro")} Selecionar ${vaga.livres ? `(${vaga.livres} ${vaga.livres === 1 ? "vaga livre" : "vagas livres"})` : "(vagas ocupadas)"}</button>
        <button class="botao vidro" type="button" data-acao="candidato">${icone("mais")} Candidato</button>
        <button class="botao vidro" type="button" data-acao="importar">Importar CSV</button>
        <button class="botao vidro" type="button" data-acao="simular">${icone("play")} Simular processo</button>
        <button class="botao vidro" type="button" data-acao="encerrar">Encerrar vaga</button></div>` : ""}
    </section>
    <div class="indicadores">
      <div class="indicador"><small>Candidatos</small><strong>${vaga.total}</strong></div>
      <div class="indicador"><small>Vagas ocupadas</small><strong>${vaga.ocupadas}/${vaga.quantidade}</strong>
        <div class="progresso"><span data-largura="${(vaga.ocupadas / vaga.quantidade) * 100}"></span></div></div>
      <div class="indicador"><small>Contratados</small><strong>${vaga.contratados}</strong></div>
      <div class="indicador"><small>Pedem acima do orçamento</small><strong>${vaga.acimaDoOrcamento}</strong></div>
    </div>
    <div class="quadro">${COLUNAS.map((e) => coluna(e, candidatos.filter((c) => c.etapa === e), aberta)).join("")}
      <section class="coluna e-ENCERRADOS" data-coluna="ENCERRADOS"><div class="coluna-topo"><span>Encerrados</span>
        <span class="contagem">${encerrados.length}</span></div>
        ${encerrados.length ? encerrados.map(cartao).join("") : `<p class="vazio-coluna">Solte aqui para reprovar ou registrar desistência</p>`}</section>
    </div>`;
  acabamento(principal);
  ligarQuadro(principal, aberta);
  $$("[data-acao]", principal).forEach((b) => b.addEventListener("click", () => acoes[b.dataset.acao]()));
  if (candidatoParaAbrir) abrirCandidato(candidatoParaAbrir, recarregar);
}

async function recarregar() {
  await telaVaga($("#principal"), estado.vaga.id);
}

function coluna(etapa, lista, aberta) {
  return `<section class="coluna e-${etapa}" data-coluna="${etapa}"><div class="coluna-topo"><span>${NOMES_ETAPAS[etapa]}</span>
      <span class="contagem">${lista.length}</span></div>
      ${lista.length ? lista.map(cartao).join("") : `<p class="vazio-coluna">${aberta ? "Arraste um candidato para cá" : "Ninguém nesta etapa"}</p>`}</section>`;
}

function cartao(c) {
  const ativo = !ENCERRADAS.includes(c.etapa) && c.etapa !== "CONTRATADO";
  const atrasado = ativo && c.etapa !== "INSCRITO" && c.diasNaEtapa >= 5;
  const extra = c.etapa === "SELECIONADO"
    ? `<div class="tentativas" title="Ligações sem resposta">${[0, 1, 2].map((i) => `<i class="${i < c.tentativasDeContato ? "feita" : ""}"></i>`).join("")}
        ${c.tentativasDeContato ? `${c.tentativasDeContato}/3 sem resposta` : "ainda sem ligação"}</div>
       <div class="contato-rapido"><button class="botao pequeno sucesso" data-contato="true" data-id="${c.id}">✓ Atendeu</button>
        <button class="botao pequeno perigo" data-contato="false" data-id="${c.id}">✕ Não</button></div>` : "";
  return `<article class="cartao" draggable="${c.proximas.length > 0}" data-id="${c.id}" tabindex="0">
      <div class="cabeca">${avatar(c.nome, "pequeno")}<span class="nome" title="${escapar(c.nome)}">${escapar(c.nome)}</span>
        ${anel(c.compatibilidade.pontuacao)}</div>
      <div class="sub">${dinheiro(c.salarioPretendido)} · ${escapar(c.origemNome)}</div>
      <div class="rodape">
        ${ENCERRADAS.includes(c.etapa) ? `<span class="selo AGUARDAR">${NOMES_ETAPAS[c.etapa]}</span>`
          : `<span class="selo ${c.recomendacao}">${{ LIGAR: "No orçamento", CONTRAPROPOSTA: "No limite", AGUARDAR: "Acima" }[c.recomendacao]}</span>`}
        ${ativo ? `<span class="selo dias ${atrasado ? "atrasado" : ""}" title="Tempo nesta etapa">${icone("relogio")} ${c.diasNaEtapa}d</span>` : ""}
        ${c.salarioOfertado ? `<span class="selo oferta">Oferta ${dinheiro(c.salarioOfertado)}</span>` : ""}
        ${estrelas(c.notaEntrevista)}
      </div>${extra}</article>`;
}

function ligarQuadro(raiz, aberta) {
  $$(".cartao", raiz).forEach((el) => {
    el.addEventListener("click", (e) => {
      if (!e.target.closest("[data-contato]")) abrirCandidato(el.dataset.id, recarregar);
    });
    el.addEventListener("keydown", (e) => { if (e.key === "Enter") abrirCandidato(el.dataset.id, recarregar); });
    if (!aberta) return;
    el.addEventListener("dragstart", (e) => {
      const c = estado.candidatos.find((x) => String(x.id) === el.dataset.id);
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
    col.addEventListener("drop", (e) => {
      e.preventDefault();
      const id = e.dataTransfer.getData("text/plain");
      if (!col.classList.contains("pode-soltar")) return;
      const destino = col.dataset.coluna;
      if (destino === "ENCERRADOS") return encerrarCandidato(id);
      // Proposta e entrevista pedem dados (avaliação e valor): a ficha do candidato abre na ação certa.
      if (destino === "PROPOSTA") return abrirCandidato(id, recarregar, "proposta");
      mover(id, destino);
    });
  });
  $$("[data-contato]", raiz).forEach((b) => b.addEventListener("click", (e) => {
    e.stopPropagation();
    registrarContato(b.dataset.id, b.dataset.contato === "true", recarregar);
  }));
}

export async function mover(id, etapa, motivo, depois = recarregar) {
  try {
    const c = await api(`/api/candidatos/${id}/etapa`, { metodo: "POST", corpo: { etapa, motivo } });
    avisar(`${c.nome} → ${NOMES_ETAPAS[etapa]}`, { tipo: "sucesso" });
    fecharModal();
    await depois();
  } catch (erro) {
    avisarErro(erro);
  }
}

export async function registrarContato(id, atendeu, depois = recarregar) {
  try {
    const r = await api(`/api/candidatos/${id}/contatos`, { metodo: "POST", corpo: { atendeu } });
    avisar(r.mensagem, { titulo: { ATENDEU: "Contato feito", NAO_ATENDEU: "Não atendeu", SEM_CONTATO: "Sem contato" }[r.resultado],
      tipo: r.resultado === "ATENDEU" ? "sucesso" : r.resultado === "SEM_CONTATO" ? "erro" : "", duracao: 7000 });
    await depois();
  } catch (erro) {
    avisarErro(erro);
  }
}

export function encerrarCandidato(id, depois = recarregar) {
  const c = estado.candidatos.find((x) => String(x.id) === String(id));
  const opcoes = c.proximas.filter((p) => p.etapa === "REPROVADO" || p.etapa === "DESISTIU");
  const corpo = abrirModal("Encerrar o processo de " + c.nome, `<form class="formulario" novalidate>
      <label class="campo"><span>Motivo</span><select name="etapa">${opcoes.map((o) =>
        `<option value="${o.etapa}">${o.etapa === "REPROVADO" ? "Reprovado pelo processo" : "Desistiu"}</option>`).join("")}</select></label>
      <label class="campo"><span>Observação (fica no histórico)</span><input name="motivo" maxlength="120" placeholder="Ex.: aceitou outra proposta"></label>
      <div class="acoes"><button class="botao primario">Confirmar</button></div></form>`,
    c.etapa === "INSCRITO" ? "" : "A vaga que ele ocupava é liberada e o próximo da fila que cabe no orçamento é chamado.");
  $("form", corpo).addEventListener("submit", (e) => {
    e.preventDefault();
    const gaveta = $("#gaveta");
    if (gaveta.open) gaveta.close();
    mover(id, e.target.etapa.value, e.target.motivo.value, depois);
  });
}

const acoes = {
  async selecionar() {
    try {
      const lista = await api(`/api/vagas/${estado.vaga.id}/selecao`, { metodo: "POST" });
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
        <div class="acoes"><button class="botao primario">${icone("play")} Simular</button></div></form><div data-registro></div>`);
    $("form", corpo).addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const s = await api(`/api/vagas/${estado.vaga.id}/simulacao?semente=${encodeURIComponent(e.target.semente.value)}`, { metodo: "POST" });
        const classe = (p) => /contratad|Conseguimos|passou|proposta$/.test(p) ? "ok" : /Não conseguimos|não passou|recusou|não atendeu/.test(p) ? "ruim" : "info";
        $("[data-registro]", corpo).innerHTML = `<div class="registro">${s.passos.map((p) => `<span class="${classe(p)}">› ${escapar(p)}</span>`).join("")}</div>`;
        $("form", corpo).hidden = true;
        await recarregar();
      } catch (erro) {
        avisarErro(erro);
      }
    });
  },
  candidato() {
    const corpo = abrirModal("Novo candidato", `<form class="formulario" novalidate>
        <label class="campo"><span>Nome</span><input name="nome" maxlength="80"></label>
        <div class="duas"><label class="campo"><span>E-mail</span><input name="email" type="email"></label>
          <label class="campo"><span>Telefone</span><input name="telefone" inputmode="tel"></label></div>
        <div class="duas"><label class="campo"><span>Salário pretendido</span><input name="salario" inputmode="decimal" placeholder="0,00"></label>
          <label class="campo"><span>Origem</span><select name="origem"><option value="LINKEDIN">LinkedIn</option><option value="INDICACAO">Indicação</option>
            <option value="MANUAL">Cadastro manual</option></select></label></div>
        <label class="campo"><span>Habilidades</span><input name="habilidades" placeholder="${escapar(estado.vaga.requisitos.join(", ") || "Separadas por vírgula")}"></label>
        <label class="campo"><span>LinkedIn (opcional)</span><input name="linkedin" placeholder="linkedin.com/in/nome"></label>
        <small class="suave">Orçamento da vaga: ${dinheiro(estado.vaga.salarioBase)}. Triagem e compatibilidade são calculadas na hora.</small>
        <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario">Inscrever</button></div></form>`);
    const f = $("form", corpo);
    f.nome.focus();
    f.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const c = await api(`/api/vagas/${estado.vaga.id}/candidatos`, { metodo: "POST", corpo: { nome: f.nome.value, email: f.email.value,
          telefone: f.telefone.value, salarioPretendido: lerValor(f.salario.value) || null, origem: f.origem.value,
          habilidades: f.habilidades.value, linkedin: f.linkedin.value } });
        fecharModal();
        avisar(`Compatibilidade ${c.compatibilidade.pontuacao}% · ${c.recomendacaoTexto.toLowerCase()}.`, { titulo: c.nome + " inscrito", tipo: "sucesso" });
        await recarregar();
      } catch (erro) {
        erroNoFormulario(f, erro);
      }
    });
  },
  importar() {
    const exemplo = "nome;email;telefone;salario;habilidades\nLeticia Moraes;leticia.m@email.com;(27) 99811-2233;3.200,00;SQL|Python\nRodrigo Sales;rodrigo.s@email.com;;3.900,00;Excel";
    const corpo = abrirModal("Importar candidatos", `<form class="formulario" novalidate>
        <label class="campo"><span>Cole as linhas da planilha</span><textarea class="codigo" name="texto" spellcheck="false">${exemplo}</textarea>
          <small>nome;e-mail;telefone;salário e, opcional, habilidades separadas por |. A primeira linha pode ser o cabeçalho.</small></label>
        <div data-resultado></div><div class="acoes"><button class="botao primario">Importar</button></div></form>`);
    const f = $("form", corpo);
    f.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const r = await api(`/api/vagas/${estado.vaga.id}/importacao`, { metodo: "POST", corpo: { texto: f.texto.value } });
        $("[data-resultado]", corpo).innerHTML = `<p><strong>${r.importados} importado(s).</strong></p>
          ${r.erros.length ? `<ul class="erro-form">${r.erros.map((x) => `<li>${escapar(x)}</li>`).join("")}</ul>` : ""}`;
        await recarregar();
      } catch (erro) {
        avisarErro(erro);
      }
    });
  },
  encerrar() {
    const corpo = abrirModal("Encerrar vaga", `<div class="formulario"><p>A vaga sai da página de carreiras e deixa de aceitar mudanças. Os candidatos continuam no histórico.</p>
      <div class="acoes"><button class="botao primario" data-sim>Encerrar vaga</button></div></div>`);
    $("[data-sim]", corpo).addEventListener("click", async () => {
      try {
        await api(`/api/vagas/${estado.vaga.id}/encerramento`, { metodo: "POST" });
        fecharModal();
        await recarregar();
      } catch (erro) {
        avisarErro(erro);
      }
    });
  },
};
