package dev.barboza.crivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.EventoRepository;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.NovaVaga;
import dev.barboza.crivo.servico.RecrutamentoService.NovoCandidato;
import dev.barboza.crivo.servico.SimulacaoService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RelogioFixo.class)
@Transactional
class SimulacaoEApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    RecrutamentoService recrutamento;

    @Autowired
    SimulacaoService simulacao;

    @Autowired
    EventoRepository eventos;

    /** O processo seletivo das aulas: salário base de R$ 2.000,00, 5 vagas e 10 candidatos. */
    private Vaga processoDasAulas() {
        Vaga vaga = recrutamento.criarVaga(new NovaVaga("Assistente", "Administrativo", null, new BigDecimal("2000"), 5));
        String[][] pessoas = {{"Felipe", "2194"}, {"Marcia", "1813"}, {"Julia", "2168"}, {"Paulo", "1954"}, {"Augusto", "2079"},
            {"Monica", "1819"}, {"Fabricio", "1861"}, {"Mirela", "1901"}, {"Daniela", "2000"}, {"Jorge", "1950"}};
        for (String[] p : pessoas) {
            recrutamento.inscrever(vaga.getId(), new NovoCandidato(p[0], p[0].toLowerCase() + "@teste.dev", null, new BigDecimal(p[1])));
        }
        return vaga;
    }

    private ResultActions postar(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void simulacaoComAMesmaSementeDaOMesmoResultado() {
        List<String> primeira = simulacao.simular(processoDasAulas().getId(), 42L).passos();
        List<String> segunda = simulacao.simular(processoDasAulas().getId(), 42L).passos();

        assertThat(primeira).isEqualTo(segunda).isNotEmpty();
        assertThat(primeira.getFirst()).startsWith("Selecionado: Marcia");
    }

    @Test
    void simulacaoRespeitaAsRegrasEmQualquerSemente() {
        for (long semente = 1; semente <= 25; semente++) {
            Vaga vaga = processoDasAulas();
            SimulacaoService.Simulacao s = simulacao.simular(vaga.getId(), semente);
            List<Candidato> todos = recrutamento.candidatosDa(vaga.getId());

            assertThat(s.funil().contratados()).isLessThanOrEqualTo(5);
            // Quem pediu acima do orçamento nunca passa de "inscrito".
            assertThat(todos).filteredOn(c -> c.getSalarioPretendido().compareTo(new BigDecimal("2000")) > 0)
                    .allMatch(c -> c.getEtapa() == Etapa.INSCRITO);
            // "Sem contato" só com 3 tentativas registradas.
            assertThat(todos).filteredOn(c -> c.getEtapa() == Etapa.SEM_CONTATO)
                    .allMatch(c -> c.getTentativasDeContato() == Candidato.MAXIMO_DE_TENTATIVAS);
            // Ao final, ninguém fica parado no meio do funil.
            assertThat(todos).noneMatch(c -> c.getEtapa() == Etapa.SELECIONADO || c.getEtapa() == Etapa.ENTREVISTA
                    || c.getEtapa() == Etapa.PROPOSTA);
        }
    }

    @Test
    void apiListaEtapasVagasECandidatos() throws Exception {
        Vaga vaga = processoDasAulas();

        mvc.perform(get("/api/etapas")).andExpect(jsonPath("$", hasSize(8))).andExpect(jsonPath("$[0].nome").value("Inscritos"));
        mvc.perform(get("/api/vagas/" + vaga.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.acimaDoOrcamento").value(3))
                .andExpect(jsonPath("$.porEtapa.INSCRITO").value(10));
        mvc.perform(get("/api/vagas/" + vaga.getId() + "/candidatos"))
                .andExpect(jsonPath("$[0].nome").value("Felipe"))
                .andExpect(jsonPath("$[0].recomendacaoTexto").value("Aguardar os demais candidatos"))
                .andExpect(jsonPath("$[0].proximas[*].etapa").value(org.hamcrest.Matchers.contains("SELECIONADO", "REPROVADO", "DESISTIU")));
    }

    @Test
    void apiCriaVagaEInscreve() throws Exception {
        String vaga = postar("/api/vagas", """
                {"titulo":"Dev Java","area":"TI","salarioBase":9000,"quantidade":2}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.livres").value(2))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        postar("/api/vagas/" + vaga + "/candidatos", """
                {"nome":"Ana Souza","email":"ana@x.com","salarioPretendido":9000}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recomendacao").value("CONTRAPROPOSTA"));
    }

    @Test
    void errosEmProblemJson() throws Exception {
        Vaga vaga = processoDasAulas();
        Long felipe = recrutamento.candidatosDa(vaga.getId()).getFirst().getId();

        mvc.perform(get("/api/vagas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        postar("/api/candidatos/" + felipe + "/etapa", "{\"etapa\":\"CONTRATADO\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Não é possível mover de \"Inscritos\" para \"Contratados\"."));
        postar("/api/candidatos/" + felipe + "/etapa", "{\"etapa\":\"SELECIONADO\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("acima do orçamento")));
        postar("/api/candidatos/" + felipe + "/etapa", "{\"etapa\":\"FERIAS\"}").andExpect(status().isBadRequest());
        postar("/api/candidatos/" + felipe + "/etapa", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Informe a etapa de destino."));
        postar("/api/vagas", "{\"titulo\":\"x\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("o título")));
    }

    @Test
    void contatoPelaApiChamaSuplente() throws Exception {
        Vaga vaga = recrutamento.criarVaga(new NovaVaga("Dev", "TI", null, new BigDecimal("3000"), 1));
        Candidato ana = recrutamento.inscrever(vaga.getId(), new NovoCandidato("Ana Souza", "ana@x.com", null, new BigDecimal("2500")));
        recrutamento.inscrever(vaga.getId(), new NovoCandidato("Bia Lima", "bia@x.com", null, new BigDecimal("2600")));
        recrutamento.selecionar(vaga.getId());

        postar("/api/candidatos/" + ana.getId() + "/contatos", "{\"atendeu\":false}")
                .andExpect(jsonPath("$.resultado").value("NAO_ATENDEU"))
                .andExpect(jsonPath("$.mensagem").value("Ana Souza não atendeu. Restam 2 tentativa(s)."));
        postar("/api/candidatos/" + ana.getId() + "/contatos", "{\"atendeu\":false}");
        postar("/api/candidatos/" + ana.getId() + "/contatos", "{\"atendeu\":false}")
                .andExpect(jsonPath("$.resultado").value("SEM_CONTATO"))
                .andExpect(jsonPath("$.suplente.nome").value("Bia Lima"))
                .andExpect(jsonPath("$.suplente.etapa").value("SELECIONADO"));

        mvc.perform(get("/api/candidatos/" + ana.getId()))
                .andExpect(jsonPath("$.candidato.etapa").value("SEM_CONTATO"))
                .andExpect(jsonPath("$.candidato.proximas", hasSize(0)))
                .andExpect(jsonPath("$.historico", hasSize(6)));
    }

    @Test
    void importacaoESimulacaoPelaApi() throws Exception {
        Vaga vaga = recrutamento.criarVaga(new NovaVaga("Dev", "TI", null, new BigDecimal("3000"), 1));

        postar("/api/vagas/" + vaga.getId() + "/importacao", "{\"texto\":\"Ana Souza;ana@x.com;;2500\\nErro;x;;1\"}")
                .andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.erros", hasSize(1)));
        mvc.perform(post("/api/vagas/" + vaga.getId() + "/simulacao").param("semente", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semente").value(7))
                .andExpect(jsonPath("$.passos[0]").value("Selecionado: Ana Souza (pretende R$ 2.500,00)"));
    }

    @Test
    void carreirasMostraSoVagasAbertasEInscreveComOrigem() throws Exception {
        Vaga vaga = recrutamento.criarVaga(new NovaVaga("Dev", "TI", null, new BigDecimal("3000"), 1, "Java, SQL", null, "Vitória, ES"));
        Vaga fechada = recrutamento.criarVaga(new NovaVaga("Antiga", "TI", null, new BigDecimal("3000"), 1));
        recrutamento.encerrarVaga(fechada.getId());

        mvc.perform(get("/api/carreiras"))
                .andExpect(jsonPath("$[?(@.titulo == 'Dev')].requisitos[0]").value("Java"))
                .andExpect(jsonPath("$[?(@.titulo == 'Antiga')]").isEmpty())
                .andExpect(jsonPath("$[0].salarioBase").doesNotExist());
        postar("/api/carreiras/" + vaga.getId() + "/candidaturas", """
                {"nome":"Ana Souza","email":"ana@x.com","salarioPretendido":2800,"habilidades":"Java, Git"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem", containsString("Recebemos sua candidatura para Dev, Ana.")));
        mvc.perform(get("/api/vagas/" + vaga.getId() + "/candidatos"))
                .andExpect(jsonPath("$[0].origem").value("CARREIRAS"))
                .andExpect(jsonPath("$[0].compatibilidade.pontuacao").value(60))
                .andExpect(jsonPath("$[0].compatibilidade.faltam[0]").value("SQL"));
    }

    @Test
    void avaliacaoEPropostaPelaApi() throws Exception {
        Vaga vaga = recrutamento.criarVaga(new NovaVaga("Dev", "TI", null, new BigDecimal("3000"), 1));
        Candidato ana = recrutamento.inscrever(vaga.getId(), new NovoCandidato("Ana Souza", "ana@x.com", null, new BigDecimal("2900")));
        recrutamento.selecionar(vaga.getId());
        recrutamento.registrarContato(ana.getId(), true);

        postar("/api/candidatos/" + ana.getId() + "/proposta", "{\"valor\":2900}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Registre a avaliação da entrevista antes de enviar a proposta."));
        postar("/api/candidatos/" + ana.getId() + "/avaliacao", "{\"nota\":4,\"parecer\":\"Boa entrevista técnica.\"}")
                .andExpect(jsonPath("$.notaEntrevista").value(4));
        postar("/api/candidatos/" + ana.getId() + "/proposta", "{\"valor\":2700}")
                .andExpect(jsonPath("$.etapa").value("PROPOSTA"))
                .andExpect(jsonPath("$.salarioOfertado").value(2700));
        postar("/api/candidatos/" + ana.getId() + "/notas", "{\"texto\":\"Responde até sexta.\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/painel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funil.PROPOSTA").value(1))
                .andExpect(jsonPath("$.atividade[0].descricao").value("Responde até sexta."));
    }

    @Test
    void siteESwaggerRespondem() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title", containsString("Crivo")));
        mvc.perform(get("/health")).andExpect(status().isOk());
    }
}
