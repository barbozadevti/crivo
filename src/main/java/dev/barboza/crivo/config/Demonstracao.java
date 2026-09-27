package dev.barboza.crivo.config;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Candidato.Origem;
import dev.barboza.crivo.dominio.CandidatoRepository;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.EventoRepository;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.dominio.VagaRepository;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.NovaVaga;
import dev.barboza.crivo.servico.RecrutamentoService.NovoCandidato;

/**
 * Vagas e candidatos de exemplo do "Grupo Horizonte" (empresa fictícia), com datas espalhadas pelas
 * últimas semanas: cada vaga está num momento diferente do processo e a visão geral tem o que cobrar.
 */
@Service
public class Demonstracao implements SmartInitializingSingleton {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final RecrutamentoService recrutamento;
    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final EventoRepository eventos;
    private final Environment ambiente;
    private final Clock relogio;
    private int sequencia;

    public Demonstracao(RecrutamentoService recrutamento, VagaRepository vagas, CandidatoRepository candidatos,
            EventoRepository eventos, Environment ambiente, Clock relogio) {
        this.recrutamento = recrutamento;
        this.vagas = vagas;
        this.candidatos = candidatos;
        this.eventos = eventos;
        this.ambiente = ambiente;
        this.relogio = relogio;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (ambiente.getProperty("crivo.demo", Boolean.class, false) && vagas.count() == 0) {
            carregar();
        }
    }

    @Transactional
    public void reiniciar() {
        eventos.deleteAllInBatch();
        candidatos.deleteAllInBatch();
        vagas.deleteAllInBatch();
        carregar();
    }

    @Transactional
    public void carregar() {
        sequencia = 0;
        java();
        suporte();
        dados();
        design();
        assistente();
        estagio();
    }

    /** Duas vagas ocupadas: uma proposta sem resposta há dias e uma entrevista avaliada, pronta para proposta. */
    private void java() {
        Vaga vaga = vaga("Desenvolvedor(a) Java Pleno", "Tecnologia", "9000", 2, Vaga.Modelo.HIBRIDO, "Vitória, ES",
                "Java, Spring Boot, SQL, Docker, Testes automatizados, Git",
                "Evolução das APIs de pagamentos: Java 21, Spring Boot, mensageria e testes automatizados. Time de 6 pessoas.", 22);
        Candidato marina = inscrever(vaga, "Marina Castro", "8500", "Java, Spring Boot, SQL, Docker, Git", Origem.LINKEDIN, 21, "09:14");
        Candidato julio = inscrever(vaga, "Júlio Pereira", "9000", "Java, Spring Boot, Testes automatizados, Git", Origem.INDICACAO, 20, "11:02");
        inscrever(vaga, "Rafael Souza", "9500", "Java, Spring Boot, Kotlin, SQL", Origem.CARREIRAS, 20, "16:40");
        inscrever(vaga, "André Lopes", "10500", "Java, Spring Boot, Kubernetes, Docker, SQL", Origem.LINKEDIN, 19, "08:55");
        inscrever(vaga, "Beatriz Gomes", "7800", "Java, SQL, Git", Origem.CARREIRAS, 18, "13:20");
        inscrever(vaga, "Felipe Araújo", "9200", "Java, Spring Boot, Docker", Origem.IMPORTACAO, 17, "10:05");
        inscrever(vaga, "Larissa Freitas", "8200", "Python, SQL, Git", Origem.CARREIRAS, 16, "19:45");
        recrutamento.selecionarEm(vaga.getId(), dia(15, "09:30"));
        recrutamento.anotarEm(julio.getId(), "Indicação do time de plataforma. Conhece bem o domínio de pagamentos.", dia(15, "09:40"));
        recrutamento.registrarContatoEm(marina.getId(), false, dia(15, "10:00"));
        recrutamento.registrarContatoEm(marina.getId(), true, dia(14, "14:30"));
        recrutamento.registrarContatoEm(julio.getId(), true, dia(14, "15:10"));
        recrutamento.avaliarEm(marina.getId(), 5, "Excelente em modelagem e testes; resolveu o desafio técnico com folga.", dia(9, "17:00"));
        recrutamento.enviarPropostaEm(marina.getId(), valor("8500"), dia(5, "11:00"));
        recrutamento.anotarEm(marina.getId(), "Pediu até sexta para responder: tem outra proposta em andamento.", dia(4, "09:15"));
        recrutamento.avaliarEm(julio.getId(), 4, "Boa base de Spring e mensageria; precisa ganhar experiência com Docker.", dia(2, "18:20"));
        inscrever(vaga, "Carolina Mendes", "8800", "Java, Spring Boot, SQL, Docker, Testes automatizados, Git", Origem.LINKEDIN, 8, "10:30");
        inscrever(vaga, "Otávio Barros", "11000", "Java, Arquitetura, AWS, SQL", Origem.LINKEDIN, 6, "15:00");
        inscrever(vaga, "Igor Tavares", "6500", "Java, Git", Origem.CARREIRAS, 1, "20:10");
        inscrever(vaga, "Natália Rocha", "8900", "Java, Spring Boot, SQL, Docker", Origem.CARREIRAS, 0, "08:12");
    }

    /** Uma contratada, uma "sem contato" (e o suplente chamado), entrevista sem avaliação. */
    private void suporte() {
        Vaga vaga = vaga("Analista de Suporte Júnior", "Tecnologia", "3500", 2, Vaga.Modelo.PRESENCIAL, "Serra, ES",
                "Atendimento, Windows, Redes, ITIL, Comunicação",
                "Atendimento de 1º e 2º nível, abertura e acompanhamento de chamados, suporte a usuários internos.", 30);
        Candidato paula = inscrever(vaga, "Paula Ribeiro", "3200", "Atendimento, Windows, Redes, Comunicação", Origem.CARREIRAS, 29, "10:00");
        inscrever(vaga, "Lucas Martins", "3800", "Windows, Redes, Linux", Origem.LINKEDIN, 29, "14:20");
        Candidato renata = inscrever(vaga, "Renata Alves", "3500", "Atendimento, ITIL, Comunicação", Origem.CARREIRAS, 28, "09:10");
        Candidato diego = inscrever(vaga, "Diego Costa", "2900", "Atendimento, Windows, ITIL", Origem.INDICACAO, 28, "18:00");
        Candidato fernanda = inscrever(vaga, "Fernanda Lima", "3400", "Excel, Comunicação", Origem.CARREIRAS, 27, "11:45");
        inscrever(vaga, "Gustavo Rocha", "4200", "Redes, Linux, Cloud", Origem.LINKEDIN, 26, "08:30");
        inscrever(vaga, "Camila Duarte", "3100", "Atendimento, Windows, Comunicação", Origem.CARREIRAS, 25, "12:15");
        Candidato thiago = inscrever(vaga, "Thiago Nunes", "3000", "Windows, Redes", Origem.IMPORTACAO, 25, "16:50");
        inscrever(vaga, "Aline Moura", "3600", "Atendimento, ITIL", Origem.CARREIRAS, 24, "09:40");
        recrutamento.selecionarEm(vaga.getId(), dia(23, "09:00"));
        recrutamento.registrarContatoEm(paula.getId(), true, dia(23, "10:15"));
        recrutamento.avaliarEm(paula.getId(), 5, "Muito clara no atendimento; resolveu os dois casos práticos.", dia(19, "15:00"));
        recrutamento.enviarPropostaEm(paula.getId(), valor("3200"), dia(16, "11:00"));
        recrutamento.moverEm(paula.getId(), Etapa.CONTRATADO, "aceitou; início em 1º de outubro", dia(14, "17:30"));
        for (int i = 0; i < Candidato.MAXIMO_DE_TENTATIVAS; i++) {
            recrutamento.registrarContatoEm(renata.getId(), false, dia(22 - i * 2, "10:30"));
        }
        recrutamento.registrarContatoEm(diego.getId(), true, dia(17, "09:20"));
        recrutamento.moverEm(fernanda.getId(), Etapa.REPROVADO, "sem experiência com atendimento técnico", dia(21, "16:00"));
        recrutamento.moverEm(thiago.getId(), Etapa.DESISTIU, "aceitou outra oferta", dia(20, "13:00"));
    }

    /** Vaga nova e remota: uma selecionada que ainda não recebeu ligação. */
    private void dados() {
        Vaga vaga = vaga("Analista de Dados", "Dados", "7000", 1, Vaga.Modelo.REMOTO, "Brasil",
                "SQL, Python, Power BI, Estatística, Excel",
                "Indicadores comerciais e de operação: modelagem, painéis em Power BI e análises em Python.", 7);
        inscrever(vaga, "Letícia Moraes", "6800", "SQL, Python, Power BI, Estatística", Origem.LINKEDIN, 6, "10:00");
        inscrever(vaga, "Rodrigo Sales", "7500", "SQL, Python, Tableau", Origem.CARREIRAS, 6, "15:30");
        inscrever(vaga, "Yasmin Correia", "6200", "Excel, Power BI, SQL", Origem.CARREIRAS, 5, "09:00");
        inscrever(vaga, "Eduardo Pinto", "7000", "Python, Estatística, Machine Learning", Origem.INDICACAO, 5, "20:15");
        inscrever(vaga, "Sabrina Teles", "5900", "Excel, Power BI", Origem.CARREIRAS, 4, "11:40");
        recrutamento.selecionarEm(vaga.getId(), dia(2, "09:00"));
        inscrever(vaga, "Vinícius Prado", "6900", "SQL, Python, Power BI, Excel, Estatística", Origem.LINKEDIN, 1, "13:00");
        inscrever(vaga, "Priscila Neves", "8000", "SQL, R, Estatística", Origem.CARREIRAS, 0, "07:45");
    }

    /** Entrevista com nota baixa: o painel sugere reprovar ou reavaliar. */
    private void design() {
        Vaga vaga = vaga("Designer de Produto (UX/UI)", "Produto", "8000", 1, Vaga.Modelo.REMOTO, "Brasil",
                "Figma, Pesquisa com usuários, Design System, Prototipação",
                "Aplicativo do cliente: pesquisa, fluxos e interface, junto com produto e engenharia.", 12);
        Candidato bruna = inscrever(vaga, "Bruna Siqueira", "7500", "Figma, Prototipação, Design System", Origem.LINKEDIN, 11, "09:30");
        inscrever(vaga, "Henrique Dias", "8500", "Figma, Pesquisa com usuários, Design System, Prototipação", Origem.LINKEDIN, 11, "17:10");
        inscrever(vaga, "Manuela Faria", "6900", "Figma, Pesquisa com usuários", Origem.CARREIRAS, 10, "12:00");
        inscrever(vaga, "Caio Ventura", "7200", "Photoshop, Illustrator", Origem.CARREIRAS, 9, "08:20");
        recrutamento.selecionarEm(vaga.getId(), dia(8, "10:00"));
        recrutamento.registrarContatoEm(bruna.getId(), true, dia(7, "14:00"));
        recrutamento.avaliarEm(bruna.getId(), 2, "Portfólio bonito, mas não conseguiu justificar decisões com dados de pesquisa.", dia(3, "16:40"));
    }

    /** O processo seletivo das aulas de controle de fluxo: salário base de R$ 2.000,00, 5 vagas, todos inscritos (bom para simular). */
    private void assistente() {
        Vaga vaga = vaga("Assistente Administrativo", "Administrativo", "2000", 5, Vaga.Modelo.PRESENCIAL, "Vitória, ES",
                "Excel, Organização, Atendimento, Pacote Office",
                "Rotinas administrativas, atendimento a fornecedores e controle de documentos e agendas.", 4);
        String[][] pessoas = {{"Felipe Moreira", "2194", "Excel, Atendimento"}, {"Márcia Oliveira", "1813", "Excel, Organização, Pacote Office"},
            {"Júlia Cardoso", "2168", "Organização"}, {"Paulo Henrique", "1954", "Atendimento, Pacote Office"},
            {"Augusto Ramos", "2079", "Excel"}, {"Mônica Farias", "1819", "Excel, Organização, Atendimento, Pacote Office"},
            {"Fabrício Lemos", "1861", "Atendimento"}, {"Mirela Santana", "1901", "Organização, Pacote Office"},
            {"Daniela Prado", "2000", "Excel, Atendimento, Organização"}, {"Jorge Vieira", "1950", "Pacote Office"}};
        for (int i = 0; i < pessoas.length; i++) {
            inscrever(vaga, pessoas[i][0], pessoas[i][1], pessoas[i][2], i % 3 == 0 ? Origem.IMPORTACAO : Origem.CARREIRAS,
                    3 - i / 4, String.format("%02d:%02d", 8 + i, i * 5));
        }
    }

    /** Vaga preenchida: fechou sozinha. */
    private void estagio() {
        Vaga vaga = vaga("Estágio em Recursos Humanos", "Pessoas", "1800", 1, Vaga.Modelo.HIBRIDO, "Vitória, ES",
                "Comunicação, Excel, Organização",
                "Apoio em recrutamento, integração de novos colaboradores e indicadores de RH.", 40);
        Candidato sofia = inscrever(vaga, "Sofia Batista", "1700", "Comunicação, Excel, Organização", Origem.CARREIRAS, 39, "10:00");
        inscrever(vaga, "Heitor Campos", "1800", "Comunicação", Origem.CARREIRAS, 38, "15:00");
        inscrever(vaga, "Lívia Rezende", "1950", "Excel, Organização", Origem.INDICACAO, 37, "09:30");
        recrutamento.selecionarEm(vaga.getId(), dia(35, "09:00"));
        recrutamento.registrarContatoEm(sofia.getId(), true, dia(35, "11:00"));
        recrutamento.avaliarEm(sofia.getId(), 4, "Comunicativa e organizada; boa entrevista com a gestora.", dia(31, "16:00"));
        recrutamento.enviarPropostaEm(sofia.getId(), valor("1700"), dia(29, "10:00"));
        recrutamento.moverEm(sofia.getId(), Etapa.CONTRATADO, "início na próxima segunda", dia(27, "14:00"));
    }

    // ---------- Apoio ----------

    private Vaga vaga(String titulo, String area, String salario, int quantidade, Vaga.Modelo modelo, String local,
            String requisitos, String descricao, int diasAtras) {
        return recrutamento.criarVagaEm(new NovaVaga(titulo, area, descricao, valor(salario), quantidade, requisitos, modelo, local),
                dia(diasAtras, "08:00"));
    }

    private Candidato inscrever(Vaga vaga, String nome, String salario, String habilidades, Origem origem, int diasAtras, String hora) {
        sequencia++;
        String usuario = Normalizer.normalize(nome.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace(' ', '.');
        String linkedin = origem == Origem.LINKEDIN ? "https://www.linkedin.com/in/" + usuario.replace('.', '-') : null;
        return recrutamento.inscreverEm(vaga, new NovoCandidato(nome, usuario + "@email.com",
                String.format("(27) 9%04d-%04d", 8100 + sequencia * 37, 1000 + sequencia * 113 % 9000),
                valor(salario), habilidades, linkedin, origem), dia(diasAtras, hora));
    }

    /** Data relativa a hoje (no fuso de Brasília), nunca no futuro. */
    private Instant dia(int diasAtras, String hora) {
        Instant agora = relogio.instant();
        Instant quando = LocalDate.now(relogio.withZone(FUSO)).minusDays(diasAtras).atTime(LocalTime.parse(hora))
                .atZone(FUSO).toInstant();
        return quando.isAfter(agora) ? agora.minusSeconds(60L * (100 - sequencia)) : quando;
    }

    private static BigDecimal valor(String texto) {
        return new BigDecimal(texto);
    }
}
