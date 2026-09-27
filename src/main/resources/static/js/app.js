// Crivo: casca (menu lateral), rotas e a página pública de carreiras.
import { $, $$, api, avatar, avisar, avisarErro, acabamento, escapar, icone } from "./ui.js";
import { telaPainel } from "./painel.js";
import { telaVagas } from "./vagas.js";
import { telaVaga } from "./quadro.js";
import { telaCarreiras } from "./carreiras.js";

const MENU = [
  { rota: "painel", nome: "Visão geral", icone: "painel" },
  { rota: "vagas", nome: "Vagas", icone: "vagas" },
];

function casca() {
  const itens = MENU.map((m) => `<a href="#/${m.rota}" data-rota="${m.rota}">${icone(m.icone)}${m.nome}</a>`).join("");
  $("#app").innerHTML = `<div class="casca">
      <aside class="lateral">
        <a class="marca" href="#/"><img src="favicon.svg" alt=""><span><strong>Crivo</strong><small>recrutamento</small></span></a>
        <nav class="menu" aria-label="Menu">${itens}
          <span class="rotulo-menu">Para candidatos</span>
          <a href="#/carreiras" target="_blank" rel="noopener">${icone("carreiras")}Página de carreiras</a>
          <span class="rotulo-menu">Integração</span>
          <a href="/swagger-ui.html" target="_blank" rel="noopener">${icone("api")}API (Swagger)</a></nav>
        <div class="recrutadora"><div class="quem">${avatar("Juliana Andrade")}<span><strong>Juliana Andrade</strong><small>Recrutadora · Grupo Horizonte</small></span></div>
          <button type="button" data-reiniciar title="Apaga tudo e recarrega as vagas de exemplo">Reiniciar demonstração</button></div>
      </aside>
      <div>
        <header class="topo-celular"><a class="marca" href="#/"><img src="favicon.svg" alt=""><strong>Crivo</strong></a>
          <nav>${MENU.map((m) => `<a class="item" href="#/${m.rota}" data-rota="${m.rota}">${m.nome}</a>`).join("")}
            <a class="item" href="#/carreiras">Carreiras</a></nav></header>
        <main class="principal" id="principal" tabindex="-1"></main>
      </div>
    </div>`;
  acabamento($("#app"));
  $("[data-reiniciar]").addEventListener("click", async () => {
    try {
      await api("/api/demonstracao/reinicio", { metodo: "POST" });
      avisar("Vagas e candidatos de exemplo recarregados.", { titulo: "Demonstração reiniciada", tipo: "sucesso" });
      location.hash = "#/painel";
      await rotear();
    } catch (erro) {
      avisarErro(erro);
    }
  });
}

async function rotear() {
  const [caminho, busca] = location.hash.replace(/^#\/?/, "").split("?");
  const partes = caminho.split("/");
  const parametros = new URLSearchParams(busca || "");
  if ($("#gaveta").open) $("#gaveta").close();

  if (partes[0] === "carreiras") {
    await telaCarreiras($("#app"));
    return;
  }
  document.title = "Crivo";
  if (!$("#principal")) casca();
  const principal = $("#principal");
  const rota = partes[0] === "vagas" ? "vagas" : "painel";
  $$("[data-rota]").forEach((a) => a.setAttribute("aria-current", a.dataset.rota === rota ? "page" : "false"));
  try {
    if (partes[0] === "vagas" && partes[1]) {
      await telaVaga(principal, partes[1], parametros.get("candidato"));
    } else if (partes[0] === "vagas") {
      await telaVagas(principal);
    } else {
      await telaPainel(principal);
    }
  } catch (erro) {
    principal.innerHTML = `<p class="carregando">${escapar(erro.message)} <a href="#/">Voltar</a></p>`;
  }
  principal.focus({ preventScroll: true });
}

window.addEventListener("hashchange", () => { rotear(); window.scrollTo({ top: 0 }); });
rotear();
