// Lista de vagas (cartões coloridos por área) e cadastro de vaga.
import { $, NOMES_ETAPAS, abrirModal, acabamento, api, classeDaArea, dinheiro, erroNoFormulario, escapar, fecharModal, icone, lerValor } from "./ui.js";

const ORDEM = ["INSCRITO", "SELECIONADO", "ENTREVISTA", "PROPOSTA", "CONTRATADO", "SEM_CONTATO", "REPROVADO", "DESISTIU"];

function funilMini(v) {
  if (!v.total) return `<div class="funil-mini"></div>`;
  return `<div class="funil-mini">${ORDEM.filter((e) => v.porEtapa[e]).map((e) =>
    `<span class="e-${e}" data-largura="${(v.porEtapa[e] / v.total) * 100}" title="${NOMES_ETAPAS[e]}: ${v.porEtapa[e]}"></span>`).join("")}</div>`;
}

export async function telaVagas(principal) {
  const vagas = await api("/api/vagas");
  const abertas = vagas.filter((v) => v.status === "ABERTA");
  principal.innerHTML = `<div class="cabecalho"><div><h1>Vagas</h1>
      <p>${abertas.length} abertas · ${vagas.reduce((s, v) => s + v.total, 0)} candidatos · ${vagas.reduce((s, v) => s + v.contratados, 0)} contratações</p></div>
      <button class="botao primario" type="button" data-nova>${icone("mais")} Nova vaga</button></div>
    <div class="grade-vagas">${vagas.map((v) => `<a class="cartao-vaga ${v.status === "ABERTA" ? "" : "encerrada"}" href="#/vagas/${v.id}">
        <div class="faixa degrade ${classeDaArea(v.area)}">
          <div class="chips"><span class="chip vidro">${escapar(v.area)}</span><span class="chip vidro">${escapar(v.modeloNome)}</span>
            ${v.status === "ABERTA" ? "" : `<span class="chip vidro">Encerrada</span>`}</div>
          <h2>${escapar(v.titulo)}</h2><p>${icone("mapa")} ${escapar(v.local || "—")} · até ${dinheiro(v.salarioBase)}</p>
        </div>
        <div class="corpo">
          <div class="chips">${v.requisitos.slice(0, 4).map((r) => `<span class="chip">${escapar(r)}</span>`).join("")}
            ${v.requisitos.length > 4 ? `<span class="chip">+${v.requisitos.length - 4}</span>` : ""}</div>
          ${funilMini(v)}
          <div class="numeros"><span><strong>${v.total}</strong>candidatos</span><span><strong>${v.ocupadas}/${v.quantidade}</strong>vagas ocupadas</span>
            <span><strong>${v.contratados}</strong>contratados</span></div>
          <div class="progresso" title="Contratações"><span data-largura="${(v.contratados / v.quantidade) * 100}"></span></div>
        </div></a>`).join("")}
      <button class="nova-vaga" type="button" data-nova>${icone("mais")} Abrir nova vaga</button></div>`;
  acabamento(principal);
  principal.querySelectorAll("[data-nova]").forEach((b) => b.addEventListener("click", novaVaga));
}

function novaVaga() {
  const corpo = abrirModal("Nova vaga", `<form class="formulario" novalidate>
      <label class="campo"><span>Título</span><input name="titulo" maxlength="80" placeholder="Ex.: Analista de Dados Pleno"></label>
      <div class="duas"><label class="campo"><span>Área</span><select name="area">
          <option>Tecnologia</option><option>Dados</option><option>Produto</option><option>Administrativo</option><option>Pessoas</option></select></label>
        <label class="campo"><span>Modelo</span><select name="modelo"><option value="HIBRIDO">Híbrido</option><option value="REMOTO">Remoto</option>
          <option value="PRESENCIAL">Presencial</option></select></label></div>
      <div class="duas"><label class="campo"><span>Local</span><input name="local" maxlength="60" placeholder="Vitória, ES"></label>
        <label class="campo"><span>Quantidade de vagas</span><input name="quantidade" type="number" min="1" max="50" value="1"></label></div>
      <label class="campo"><span>Salário base (orçamento)</span><input name="salario" inputmode="decimal" placeholder="4.500,00">
        <small>Quem pretender até este valor passa na triagem.</small></label>
      <label class="campo"><span>Requisitos</span><input name="requisitos" placeholder="SQL, Python, Power BI">
        <small>Separados por vírgula. Viram a base da compatibilidade de cada candidato.</small></label>
      <label class="campo"><span>Descrição</span><textarea name="descricao" maxlength="500"></textarea></label>
      <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario">Criar vaga</button></div></form>`);
  const f = $("form", corpo);
  f.titulo.focus();
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const vaga = await api("/api/vagas", { metodo: "POST", corpo: { titulo: f.titulo.value, area: f.area.value, modelo: f.modelo.value,
        local: f.local.value, descricao: f.descricao.value, requisitos: f.requisitos.value,
        salarioBase: lerValor(f.salario.value) || null, quantidade: Number(f.quantidade.value) } });
      fecharModal();
      location.hash = "#/vagas/" + vaga.id;
    } catch (erro) {
      erroNoFormulario(f, erro);
    }
  });
}
