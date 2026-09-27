package dev.barboza.crivo.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.CandidatoRepository;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.EventoRepository;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.dominio.VagaRepository;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.NovaVaga;
import dev.barboza.crivo.servico.RecrutamentoService.NovoCandidato;

/**
 * Vagas e candidatos de exemplo, em diferentes momentos do processo. Carregados na primeira
 * execução (antes de o servidor aceitar requisições) e recarregados pelo botão "Reiniciar demonstração".
 */
@Service
public class Demonstracao implements SmartInitializingSingleton {

    private final RecrutamentoService recrutamento;
    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final EventoRepository eventos;
    private final Environment ambiente;
    private final Clock relogio;

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
        // 1. Vaga em andamento: um contratado, um em entrevista, um sem contato (e o suplente chamado).
        Vaga suporte = recrutamento.criarVaga(new NovaVaga("Analista de Suporte Júnior", "Tecnologia",
                "Atendimento de 1º e 2º nível, abertura e acompanhamento de chamados, suporte a usuários internos.",
                valor("3500"), 2));
        inscrever(suporte, 18, List.of(
                c("Paula Ribeiro", "3200"), c("Lucas Martins", "3800"), c("Renata Alves", "3500"), c("Diego Costa", "2900"),
                c("Fernanda Lima", "3400"), c("Gustavo Rocha", "4200"), c("Camila Duarte", "3100"), c("Bruno Teixeira", "3300"),
                c("Aline Moura", "3600"), c("Thiago Nunes", "3000")));
        recrutamento.selecionar(suporte.getId());
        Candidato paula = pessoa(suporte, "Paula Ribeiro");
        recrutamento.registrarContato(paula.getId(), true);
        recrutamento.mover(paula.getId(), Etapa.PROPOSTA, "ótima entrevista técnica");
        recrutamento.mover(paula.getId(), Etapa.CONTRATADO, "aceitou a proposta");
        Candidato renata = pessoa(suporte, "Renata Alves");
        for (int i = 0; i < Candidato.MAXIMO_DE_TENTATIVAS; i++) {
            recrutamento.registrarContato(renata.getId(), false);
        }
        recrutamento.registrarContato(pessoa(suporte, "Diego Costa").getId(), true);
        recrutamento.mover(pessoa(suporte, "Fernanda Lima").getId(), Etapa.REPROVADO, "sem experiência com atendimento");
        recrutamento.mover(pessoa(suporte, "Thiago Nunes").getId(), Etapa.DESISTIU, "aceitou outra oferta");

        // 2. Vaga recém-selecionada: dois candidatos para ligar (um já com uma tentativa).
        Vaga java = recrutamento.criarVaga(new NovaVaga("Desenvolvedor Java Pleno", "Tecnologia",
                "Java 21, Spring Boot, JPA e testes automatizados. Modelo híbrido.", valor("9000"), 2));
        inscrever(java, 9, List.of(
                c("Marina Castro", "8500"), c("Rafael Souza", "9500"), c("Júlio Pereira", "9000"), c("Beatriz Gomes", "7800"),
                c("André Lopes", "10500"), c("Carolina Mendes", "8800"), c("Felipe Araújo", "9200"), c("Larissa Freitas", "8200"),
                c("Otávio Barros", "11000")));
        recrutamento.selecionar(java.getId());
        recrutamento.registrarContato(pessoa(java, "Marina Castro").getId(), false);

        // 3. O processo seletivo das aulas: salário base de R$ 2.000,00 e 5 vagas. Todos inscritos, pronto para simular.
        Vaga assistente = recrutamento.criarVaga(new NovaVaga("Assistente Administrativo", "Administrativo",
                "O caso do processo seletivo das aulas de controle de fluxo: use \"Simular processo\".", valor("2000"), 5));
        inscrever(assistente, 4, List.of(
                c("Felipe Moreira", "2194"), c("Márcia Oliveira", "1813"), c("Júlia Cardoso", "2168"), c("Paulo Henrique", "1954"),
                c("Augusto Ramos", "2079"), c("Mônica Farias", "1819"), c("Fabrício Lemos", "1861"), c("Mirela Santana", "1901"),
                c("Daniela Prado", "2000"), c("Jorge Vieira", "1950")));

        // 4. Vaga já encerrada: preenchida, fechou sozinha.
        Vaga estagio = recrutamento.criarVaga(new NovaVaga("Estágio em Recursos Humanos", "Pessoas",
                "Apoio em recrutamento, integração de novos colaboradores e indicadores de RH.", valor("1800"), 1));
        inscrever(estagio, 30, List.of(c("Sofia Batista", "1700"), c("Heitor Campos", "1800"), c("Lívia Rezende", "1950"),
                c("Caio Monteiro", "1600")));
        recrutamento.selecionar(estagio.getId());
        Candidato sofia = pessoa(estagio, "Sofia Batista");
        recrutamento.registrarContato(sofia.getId(), true);
        recrutamento.mover(sofia.getId(), Etapa.PROPOSTA, null);
        recrutamento.mover(sofia.getId(), Etapa.CONTRATADO, "início na próxima segunda");
    }

    private record Pessoa(String nome, String salario) {
    }

    private static Pessoa c(String nome, String salario) {
        return new Pessoa(nome, salario);
    }

    /** Inscreve em ordem, espalhando as inscrições ao longo dos últimos {@code diasAtras} dias. */
    private void inscrever(Vaga vaga, int diasAtras, List<Pessoa> pessoas) {
        Instant inicio = relogio.instant().minus(diasAtras, ChronoUnit.DAYS);
        long passo = Math.max(1, diasAtras * 24L * 60 / (pessoas.size() + 1));
        for (int i = 0; i < pessoas.size(); i++) {
            Pessoa p = pessoas.get(i);
            String email = p.nome().toLowerCase()
                    .replace('á', 'a').replace('â', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o')
                    .replace('ô', 'o').replace('ú', 'u').replace('ç', 'c').replace(' ', '.') + "@email.com";
            recrutamento.inscreverEm(vaga, new NovoCandidato(p.nome(), email, "(27) 9" + (8100 + i * 37) + "-" + (1000 + i * 111),
                    valor(p.salario())), inicio.plus(passo * (i + 1), ChronoUnit.MINUTES));
        }
    }

    private Candidato pessoa(Vaga vaga, String nome) {
        return candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vaga.getId()).stream()
                .filter(c -> c.getNome().equals(nome)).findFirst().orElseThrow();
    }

    private static BigDecimal valor(String texto) {
        return new BigDecimal(texto);
    }
}
