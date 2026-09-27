// Página pública "Trabalhe conosco" do Grupo Horizonte (empresa fictícia): o lado do candidato.
import { $, $$, abrirModal, api, erroNoFormulario, escapar, icone, lerValor } from "./ui.js";

export async function telaCarreiras(raiz) {
  const vagas = await api("/api/carreiras");
  document.title = "Carreiras · Grupo Horizonte";
  raiz.innerHTML = `<div class="carreiras">
    <header class="carreiras-capa"><div class="carreiras-conteudo">
      <div class="carreiras-barra"><span class="empresa"><span class="sol"></span>Grupo Horizonte</span>
        <a href="#/">Área do recrutador →</a></div>
      <h1>Venha construir o que vem depois do horizonte.</h1>
      <p>Somos ${vagas.length ? "um time que está crescendo" : "um time em crescimento"}: tecnologia, dados, produto e pessoas trabalhando juntos, com autonomia e aprendizado contínuo.</p>
      <div class="valores"><span>🌱 Plano de desenvolvimento</span><span>🏡 Trabalho híbrido ou remoto</span><span>🩺 Plano de saúde desde o 1º dia</span><span>📚 Auxílio educação</span></div>
    </div></header>
    <main class="carreiras-conteudo">
      <div class="lista-publica">${vagas.length ? vagas.map((v) => `<article class="vaga-publica">
          <div><h2>${escapar(v.titulo)}</h2>
            <p>${escapar(v.descricao || "")}</p>
            <div class="chips"><span class="chip">${escapar(v.area)}</span><span class="chip">${icone("predio")} ${escapar(v.modelo)}</span>
              <span class="chip">${icone("mapa")} ${escapar(v.local || "Brasil")}</span>
              ${v.requisitos.map((r) => `<span class="chip">${escapar(r)}</span>`).join("")}</div></div>
          <button class="botao" type="button" data-candidatar="${v.id}">Quero me candidatar</button>
        </article>`).join("") : `<article class="vaga-publica"><p>Nenhuma vaga aberta no momento. Volte em breve!</p></article>`}</div>
    </main>
    <footer class="carreiras-rodape">Grupo Horizonte é uma empresa fictícia. Vagas gerenciadas com <a href="#/">Crivo</a>.</footer>
  </div>`;
  $$("[data-candidatar]", raiz).forEach((b) => b.addEventListener("click", () => candidatar(vagas.find((v) => String(v.id) === b.dataset.candidatar))));
}

function candidatar(vaga) {
  const corpo = abrirModal("Candidatura: " + vaga.titulo, `<form class="formulario" novalidate>
      <label class="campo"><span>Nome completo</span><input name="nome" autocomplete="name" maxlength="80"></label>
      <div class="duas"><label class="campo"><span>E-mail</span><input name="email" type="email" autocomplete="email"></label>
        <label class="campo"><span>Celular</span><input name="telefone" inputmode="tel" autocomplete="tel"></label></div>
      <div class="duas"><label class="campo"><span>Pretensão salarial</span><input name="salario" inputmode="decimal" placeholder="0,00"></label>
        <label class="campo"><span>LinkedIn (opcional)</span><input name="linkedin" placeholder="linkedin.com/in/seu-nome"></label></div>
      <label class="campo"><span>Suas principais habilidades</span><input name="habilidades" placeholder="${escapar(vaga.requisitos.slice(0, 3).join(", ") || "Separadas por vírgula")}">
        <small>Separadas por vírgula.</small></label>
      <small class="suave">Seus dados são usados só neste processo seletivo.</small>
      <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario">Enviar candidatura</button></div></form>`, vaga.area + " · " + vaga.modelo);
  const f = $("form", corpo);
  f.nome.focus();
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const r = await api(`/api/carreiras/${vaga.id}/candidaturas`, { metodo: "POST", corpo: { nome: f.nome.value, email: f.email.value,
        telefone: f.telefone.value, salarioPretendido: lerValor(f.salario.value) || null, linkedin: f.linkedin.value, habilidades: f.habilidades.value } });
      corpo.innerHTML = `<div class="sucesso-candidatura"><span class="ok">✓</span><h3>Candidatura enviada!</h3><p class="suave">${escapar(r.mensagem)}</p></div>`;
    } catch (erro) {
      erroNoFormulario(f, erro);
    }
  });
}
