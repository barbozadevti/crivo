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
import dev.barboza.crivo.dominio.Dinheiro;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Evento;
import dev.barboza.crivo.dominio.EventoRepository;
import dev.barboza.crivo.dominio.NaoEncontradoException;
import dev.barboza.crivo.dominio.Recomendacao;
import dev.barboza.crivo.dominio.RegraVioladaException;
import dev.barboza.crivo.dominio.ResultadoDoContato;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.dominio.VagaRepository;

/**
 * Regras do processo seletivo: triagem, seleção, contato, suplentes, avaliação, proposta e
 * encerramento da vaga. Os métodos terminados em {@code Em} recebem a data (dados de demonstração).
 */
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

    public record NovaVaga(String titulo, String area, String descricao, BigDecimal salarioBase, Integer quantidade,
            String requisitos, Vaga.Modelo modelo, String local) {

        public NovaVaga(String titulo, String area, String descricao, BigDecimal salarioBase, Integer quantidade) {
            this(titulo, area, descricao, salarioBase, quantidade, null, null, null);
        }
    }

    public record NovoCandidato(String nome, String email, String telefone, BigDecimal salarioPretendido,
            String habilidades, String linkedin, Candidato.Origem origem) {

        public NovoCandidato(String nome, String email, String telefone, BigDecimal salarioPretendido) {
            this(nome, email, telefone, salarioPretendido, null, null, Candidato.Origem.MANUAL);
        }
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
        return criarVagaEm(dados, relogio.instant());
    }

    @Transactional
    public Vaga criarVagaEm(NovaVaga dados, Instant quando) {
        return vagas.save(new Vaga(dados.titulo(), dados.area(), dados.descricao(), dados.salarioBase(),
                dados.quantidade() == null ? 0 : dados.quantidade(), dados.requisitos(), dados.modelo(), dados.local(), quando));
    }

    @Transactional(readOnly = true)
    public List<Funil> vagas() {
        return vagas.findAllByOrderByStatusAscCriadaEmDesc().stream().map(v -> funil(v, candidatosDa(v.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<Vaga> vagasAbertas() {
        return vagas.findAllByOrderByStatusAscCriadaEmDesc().stream().filter(Vaga::aberta).toList();
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

    @Transactional
    public Candidato inscreverEm(Vaga vaga, NovoCandidato dados, Instant quando) {
        Candidato candidato = new Candidato(vaga, dados.nome(), dados.email(), dados.telefone(), dados.salarioPretendido(),
                dados.habilidades(), dados.linkedin(), dados.origem(), quando);
        if (candidatos.existsByVagaIdAndEmail(vaga.getId(), candidato.getEmail())) {
            throw new RegraVioladaException(candidato.getEmail() + " já está inscrito nesta vaga.");
        }
        candidatos.save(candidato);
        eventos.save(Evento.inscricao(candidato, quando));
        return candidato;
    }

    /**
     * Importa candidatos colados de uma planilha: {@code nome;e-mail;telefone;salário} e, opcionalmente,
     * uma quinta coluna com habilidades separadas por "|". Linhas com erro voltam na lista de erros.
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
                if (partes.length != 4 && partes.length != 5) {
                    throw new RegraVioladaException("use nome;e-mail;telefone;salário");
                }
                String habilidades = partes.length == 5 ? partes[4].replace('|', ',') : null;
                inscreverEm(vaga, new NovoCandidato(partes[0], partes[1], partes[2], lerValor(partes[3]), habilidades, null,
                        Candidato.Origem.IMPORTACAO), relogio.instant());
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

    /** Seleciona, em ordem de inscrição, quem cabe no orçamento até completar as vagas. */
    @Transactional
    public List<Candidato> selecionar(Long vagaId) {
        Vaga vaga = vaga(vagaId);
        vaga.exigirAberta();
        List<Candidato> selecionados = selecionarEm(vagaId, relogio.instant());
        if (selecionados.isEmpty()) {
            throw new RegraVioladaException(livres(vaga) <= 0
                    ? "Todas as vagas já estão ocupadas por candidatos em andamento."
                    : "Nenhum inscrito cabe no orçamento de " + Dinheiro.formatar(vaga.getSalarioBase()) + ".");
        }
        return selecionados;
    }

    /** Como {@link #selecionar}, mas devolve lista vazia em vez de erro (simulação e demonstração). */
    @Transactional
    public List<Candidato> selecionarEm(Long vagaId, Instant quando) {
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
                registrar(candidato.mover(Etapa.SELECIONADO, "seleção automática (cabe no orçamento)", quando));
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
        return moverEm(candidatoId, destino, motivo, relogio.instant());
    }

    @Transactional
    public Candidato moverEm(Long candidatoId, Etapa destino, String motivo, Instant quando) {
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
        registrar(candidato.mover(destino, motivo, quando));
        depoisDeMover(vaga, ocupava, candidato, quando);
        return candidato;
    }

    /** Registra uma ligação. Na terceira sem sucesso, o candidato sai e o suplente é chamado. */
    @Transactional
    public ResultadoDoContato registrarContato(Long candidatoId, boolean atendeu) {
        return registrarContatoEm(candidatoId, atendeu, relogio.instant());
    }

    @Transactional
    public ResultadoDoContato registrarContatoEm(Long candidatoId, boolean atendeu, Instant quando) {
        Candidato candidato = candidato(candidatoId);
        Vaga vaga = candidato.getVaga();
        vaga.exigirAberta();
        candidato.exigirSelecionado();
        int tentativa = candidato.tentativaAtual();
        if (atendeu) {
            registrar(Evento.contato(candidato, "Atendeu na " + tentativa + "ª tentativa.", quando));
            registrar(candidato.mover(Etapa.ENTREVISTA, "contato feito", quando));
            return new ResultadoDoContato.Atendeu(candidato, tentativa);
        }
        int feitas = candidato.registrarTentativaSemSucesso(quando);
        registrar(Evento.contato(candidato, "Não atendeu (" + feitas + "ª tentativa).", quando));
        if (feitas < Candidato.MAXIMO_DE_TENTATIVAS) {
            return new ResultadoDoContato.NaoAtendeu(candidato, feitas, Candidato.MAXIMO_DE_TENTATIVAS - feitas);
        }
        registrar(candidato.mover(Etapa.SEM_CONTATO, Candidato.MAXIMO_DE_TENTATIVAS + " tentativas sem resposta", quando));
        return new ResultadoDoContato.SemContato(candidato, chamarSuplente(vaga, quando).orElse(null));
    }

    // ---------- Avaliação, proposta e notas ----------

    @Transactional
    public Candidato avaliar(Long candidatoId, int nota, String parecer) {
        return avaliarEm(candidatoId, nota, parecer, relogio.instant());
    }

    @Transactional
    public Candidato avaliarEm(Long candidatoId, int nota, String parecer, Instant quando) {
        Candidato candidato = candidato(candidatoId);
        candidato.getVaga().exigirAberta();
        registrar(candidato.avaliar(nota, parecer, quando));
        return candidato;
    }

    @Transactional
    public Candidato enviarProposta(Long candidatoId, BigDecimal valor) {
        return enviarPropostaEm(candidatoId, valor, relogio.instant());
    }

    @Transactional
    public Candidato enviarPropostaEm(Long candidatoId, BigDecimal valor, Instant quando) {
        Candidato candidato = candidato(candidatoId);
        candidato.getVaga().exigirAberta();
        registrar(candidato.enviarProposta(valor, quando));
        return candidato;
    }

    @Transactional
    public Evento anotar(Long candidatoId, String texto) {
        return anotarEm(candidatoId, texto, relogio.instant());
    }

    @Transactional
    public Evento anotarEm(Long candidatoId, String texto, Instant quando) {
        Evento nota = Evento.nota(candidato(candidatoId), texto, quando);
        registrar(nota);
        return nota;
    }

    // ---------- Apoio ----------

    /** Depois de uma mudança: libera vaga (chama suplente) ou encerra a vaga quando todas foram preenchidas. */
    private void depoisDeMover(Vaga vaga, boolean ocupava, Candidato candidato, Instant quando) {
        if (candidato.getEtapa() == Etapa.CONTRATADO) {
            long contratados = candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vaga.getId()).stream()
                    .filter(c -> c.getEtapa() == Etapa.CONTRATADO).count();
            if (contratados >= vaga.getQuantidade()) {
                vaga.encerrar(quando);
            }
        } else if (ocupava && !candidato.getEtapa().ocupaVaga()) {
            chamarSuplente(vaga, quando);
        }
    }

    /** O próximo inscrito que cabe no orçamento ocupa a vaga liberada. */
    private Optional<Candidato> chamarSuplente(Vaga vaga, Instant quando) {
        if (!vaga.aberta() || livres(vaga) <= 0) {
            return Optional.empty();
        }
        Optional<Candidato> suplente = candidatos.findByVagaIdOrderByInscritoEmAscIdAsc(vaga.getId()).stream()
                .filter(c -> c.getEtapa() == Etapa.INSCRITO && c.recomendacao().cabeNoOrcamento())
                .findFirst();
        suplente.ifPresent(c -> registrar(c.mover(Etapa.SELECIONADO, "suplente chamado para a vaga liberada", quando)));
        return suplente;
    }

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
