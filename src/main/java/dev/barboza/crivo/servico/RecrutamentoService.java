package dev.barboza.crivo.servico;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.CandidatoRepository;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Evento;
import dev.barboza.crivo.dominio.EventoRepository;
import dev.barboza.crivo.dominio.NaoEncontradoException;
import dev.barboza.crivo.dominio.Recomendacao;
import dev.barboza.crivo.dominio.RegraVioladaException;
import dev.barboza.crivo.dominio.ResultadoDoContato;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.dominio.VagaRepository;

/** Regras do processo seletivo: triagem, seleção, contato, suplentes e encerramento da vaga. */
@Service
public class RecrutamentoService {

    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final EventoRepository eventos;
    private final Clock relogio;

    public RecrutamentoService(VagaRepository vagas, CandidatoRepository candidatos, EventoRepository eventos, Clock relogio) {
        this.vagas = vagas;
        this.candidatos = candidatos;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    public record NovaVaga(String titulo, String area, String descricao, BigDecimal salarioBase, Integer quantidade) {
    }

    public record NovoCandidato(String nome, String email, String telefone, BigDecimal salarioPretendido) {
    }

    /** Números da vaga: quantos em cada etapa, vagas ocupadas e quantos pediram acima do orçamento. */
    public record Funil(Vaga vaga, Map<Etapa, Long> porEtapa, int ocupadas, int acimaDoOrcamento, long total) {

        public int livres() {
            return Math.max(0, vaga.getQuantidade() - ocupadas);
        }

        public long contratados() {
            return porEtapa.getOrDefault(Etapa.CONTRATADO, 0L);
        }
    }

    public record Importacao(int importados, List<String> erros) {
    }

    // ---------- Vagas ----------

    @Transactional
    public Vaga criarVaga(NovaVaga dados) {
        return vagas.save(new Vaga(dados.titulo(), dados.area(), dados.descricao(), dados.salarioBase(),
                dados.quantidade() == null ? 0 : dados.quantidade(), relogio.instant()));
    }

    @Transactional(readOnly = true)
    public List<Funil> vagas() {
        return vagas.findAllByOrderByStatusAscCriadaEmDesc().stream().map(v -> funil(v, candidatosDa(v.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public Funil funil(Long vagaId) {
        Vaga vaga = vaga(vagaId);
        return funil(vaga, candidatosDa(vagaId));
    }

    @Transactional
    public Vaga encerrarVaga(Long vagaId) {
        Vaga vaga = vaga(vagaId);
        vaga.encerrar(relogio.instant());
        return vaga;
    }

    // ---------- Candidatos ----------

    @Transactional(readOnly = true)
    public List<Candidato> candidatosDa(Long vagaId) {
        vaga(vagaId);
        return candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vagaId);
    }

    @Transactional(readOnly = true)
    public Candidato candidato(Long id) {
        return candidatos.findById(id).orElseThrow(() -> new NaoEncontradoException("Candidato não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Evento> historico(Long candidatoId) {
        candidato(candidatoId);
        return eventos.findByCandidatoIdOrderByDataHoraAscIdAsc(candidatoId);
    }

    @Transactional
    public Candidato inscrever(Long vagaId, NovoCandidato dados) {
        return inscreverEm(vaga(vagaId), dados, relogio.instant());
    }

    /** Inscrição com data informada (dados de demonstração). */
    @Transactional
    public Candidato inscreverEm(Vaga vaga, NovoCandidato dados, Instant quando) {
        Candidato candidato = new Candidato(vaga, dados.nome(), dados.email(), dados.telefone(), dados.salarioPretendido(), quando);
        if (candidatos.existsByVagaIdAndEmail(vaga.getId(), candidato.getEmail())) {
            throw new RegraVioladaException(candidato.getEmail() + " já está inscrito nesta vaga.");
        }
        candidatos.save(candidato);
        eventos.save(Evento.inscricao(candidato, quando));
        return candidato;
    }

    /**
     * Importa candidatos colados de uma planilha: uma linha por candidato, com
     * {@code nome;e-mail;telefone;salário} (vírgula como separador também vale). Linhas com erro
     * não impedem as demais: voltam na lista de erros.
     */
    @Transactional
    public Importacao importar(Long vagaId, String texto) {
        Vaga vaga = vaga(vagaId);
        vaga.exigirAberta();
        List<String> erros = new ArrayList<>();
        int importados = 0;
        String[] linhas = texto == null ? new String[0] : texto.strip().split("\\R");
        for (int i = 0; i < linhas.length; i++) {
            String linha = linhas[i].strip();
            if (linha.isEmpty() || (i == 0 && linha.toLowerCase().startsWith("nome"))) {
                continue;
            }
            String[] partes = linha.split(linha.contains(";") ? ";" : ",", -1);
            try {
                if (partes.length != 4) {
                    throw new RegraVioladaException("use nome;e-mail;telefone;salário");
                }
                BigDecimal salario = lerValor(partes[3]);
                inscreverEm(vaga, new NovoCandidato(partes[0], partes[1], partes[2], salario), relogio.instant());
                importados++;
            } catch (RegraVioladaException e) {
                erros.add("Linha " + (i + 1) + ": " + e.getMessage());
            }
        }
        if (importados == 0 && erros.isEmpty()) {
            throw new RegraVioladaException("Cole ao menos uma linha no formato nome;e-mail;telefone;salário.");
        }
        return new Importacao(importados, erros);
    }

    // ---------- Seleção ----------

    /**
     * Percorre a fila em ordem de inscrição e seleciona quem cabe no orçamento, até completar
     * as vagas (o {@code while} do processo seletivo das aulas).
     */
    @Transactional
    public List<Candidato> selecionar(Long vagaId) {
        Vaga vaga = vaga(vagaId);
        vaga.exigirAberta();
        List<Candidato> selecionados = selecionarSemFalhar(vagaId);
        if (selecionados.isEmpty()) {
            throw new RegraVioladaException(livres(vaga) <= 0
                    ? "Todas as vagas já estão ocupadas por candidatos em andamento."
                    : "Nenhum inscrito cabe no orçamento de " + Dinheiro.formatar(vaga.getSalarioBase()) + ".");
        }
        return selecionados;
    }

    /** Como {@link #selecionar}, mas devolve lista vazia em vez de erro (usado na simulação). */
    @Transactional
    public List<Candidato> selecionarSemFalhar(Long vagaId) {
        Vaga vaga = vaga(vagaId);
        if (!vaga.aberta()) {
            return List.of();
        }
        List<Candidato> fila = candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vagaId);
        int livres = vaga.getQuantidade() - ocupadas(fila);
        List<Candidato> selecionados = new ArrayList<>();
        int posicao = 0;
        while (livres > 0 && posicao < fila.size()) {
            Candidato candidato = fila.get(posicao);
            if (candidato.getEtapa() == Etapa.INSCRITO && candidato.recomendacao().cabeNoOrcamento()) {
                registrar(candidato.mover(Etapa.SELECIONADO, "seleção automática (cabe no orçamento)", relogio.instant()));
                selecionados.add(candidato);
                livres--;
            }
            posicao++;
        }
        return selecionados;
    }

    // ---------- Mudança de etapa ----------

    @Transactional
    public Candidato mover(Long candidatoId, Etapa destino, String motivo) {
        Candidato candidato = candidato(candidatoId);
        Vaga vaga = candidato.getVaga();
        vaga.exigirAberta();
        if (destino == Etapa.SEM_CONTATO) {
            throw new RegraVioladaException("\"Sem contato\" é definido pelo Crivo depois de "
                    + Candidato.MAXIMO_DE_TENTATIVAS + " tentativas de ligação registradas.");
        }
        if (destino == Etapa.SELECIONADO && livres(vaga) <= 0) {
            throw new RegraVioladaException("Todas as " + vaga.getQuantidade()
                    + " vagas já estão ocupadas por candidatos em andamento.");
        }
        boolean ocupava = candidato.getEtapa().ocupaVaga();
        registrar(candidato.mover(destino, motivo, relogio.instant()));
        depoisDeMover(vaga, ocupava, candidato);
        return candidato;
    }

    /** Registra uma ligação. Na terceira sem sucesso, o candidato sai e o suplente é chamado. */
    @Transactional
    public ResultadoDoContato registrarContato(Long candidatoId, boolean atendeu) {
        Candidato candidato = candidato(candidatoId);
        Vaga vaga = candidato.getVaga();
        vaga.exigirAberta();
        candidato.exigirSelecionado();
        Instant agora = relogio.instant();
        int tentativa = candidato.tentativaAtual();
        if (atendeu) {
            registrar(Evento.contato(candidato, "Atendeu na " + tentativa + "ª tentativa.", agora));
            registrar(candidato.mover(Etapa.ENTREVISTA, "contato feito", agora));
            return new ResultadoDoContato.Atendeu(candidato, tentativa);
        }
        int feitas = candidato.registrarTentativaSemSucesso(agora);
        registrar(Evento.contato(candidato, "Não atendeu (" + feitas + "ª tentativa).", agora));
        if (feitas < Candidato.MAXIMO_DE_TENTATIVAS) {
            return new ResultadoDoContato.NaoAtendeu(candidato, feitas, Candidato.MAXIMO_DE_TENTATIVAS - feitas);
        }
        registrar(candidato.mover(Etapa.SEM_CONTATO, Candidato.MAXIMO_DE_TENTATIVAS + " tentativas sem resposta", agora));
        return new ResultadoDoContato.SemContato(candidato, chamarSuplente(vaga).orElse(null));
    }

    /** Depois de uma mudança: libera vaga (chama suplente) ou encerra a vaga quando todas foram preenchidas. */
    private void depoisDeMover(Vaga vaga, boolean ocupava, Candidato candidato) {
        if (candidato.getEtapa() == Etapa.CONTRATADO) {
            long contratados = candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vaga.getId()).stream()
                    .filter(c -> c.getEtapa() == Etapa.CONTRATADO).count();
            if (contratados >= vaga.getQuantidade()) {
                vaga.encerrar(relogio.instant());
            }
        } else if (ocupava && !candidato.getEtapa().ocupaVaga()) {
            chamarSuplente(vaga);
        }
    }

    /** O próximo inscrito que cabe no orçamento ocupa a vaga liberada. */
    private Optional<Candidato> chamarSuplente(Vaga vaga) {
        if (!vaga.aberta() || livres(vaga) <= 0) {
            return Optional.empty();
        }
        Optional<Candidato> suplente = candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vaga.getId()).stream()
                .filter(c -> c.getEtapa() == Etapa.INSCRITO && c.recomendacao().cabeNoOrcamento())
                .findFirst();
        suplente.ifPresent(c -> registrar(c.mover(Etapa.SELECIONADO, "suplente chamado para a vaga liberada", relogio.instant())));
        return suplente;
    }

    // ---------- Apoio ----------

    private Vaga vaga(Long id) {
        return vagas.findById(id).orElseThrow(() -> new NaoEncontradoException("Vaga não encontrada."));
    }

    private int livres(Vaga vaga) {
        return vaga.getQuantidade() - ocupadas(candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vaga.getId()));
    }

    private static int ocupadas(List<Candidato> lista) {
        return (int) lista.stream().filter(c -> c.getEtapa().ocupaVaga()).count();
    }

    private static Funil funil(Vaga vaga, List<Candidato> lista) {
        Map<Etapa, Long> porEtapa = new EnumMap<>(Etapa.class);
        for (Etapa e : Etapa.values()) {
            porEtapa.put(e, 0L);
        }
        lista.forEach(c -> porEtapa.merge(c.getEtapa(), 1L, Long::sum));
        int acima = (int) lista.stream().filter(c -> c.recomendacao() == Recomendacao.AGUARDAR).count();
        return new Funil(vaga, porEtapa, ocupadas(lista), acima, lista.size());
    }

    private void registrar(Evento evento) {
        eventos.save(evento);
    }

    private static BigDecimal lerValor(String texto) {
        String limpo = texto.trim().replace("R$", "").replace(" ", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new RegraVioladaException("salário inválido (" + texto.trim() + ")");
        }
    }
}
