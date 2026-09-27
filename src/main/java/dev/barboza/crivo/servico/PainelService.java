package dev.barboza.crivo.servico;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.CandidatoRepository;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Evento;
import dev.barboza.crivo.dominio.EventoRepository;
import dev.barboza.crivo.dominio.Vaga;

/** Visão geral do recrutador: indicadores, o que precisa de atenção hoje e a atividade recente. */
@Service
public class PainelService {

    /** Proposta sem resposta há mais do que isso vira alerta. */
    static final int DIAS_PARA_COBRAR_PROPOSTA = 3;

    private final RecrutamentoService recrutamento;
    private final CandidatoRepository candidatos;
    private final EventoRepository eventos;
    private final Clock relogio;

    public PainelService(RecrutamentoService recrutamento, CandidatoRepository candidatos, EventoRepository eventos, Clock relogio) {
        this.recrutamento = recrutamento;
        this.candidatos = candidatos;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    public enum TipoDeAtencao { SELECIONAR, LIGAR, TENTAR_DE_NOVO, AVALIAR, ENVIAR_PROPOSTA, REVER_AVALIACAO, COBRAR_PROPOSTA }

    public record Atencao(TipoDeAtencao tipo, int prioridade, String titulo, String detalhe, Long candidatoId, Long vagaId,
            String vaga, long dias) {
    }

    public record Atividade(Instant dataHora, String tipo, String descricao, String candidato, Long candidatoId, String vaga,
            Long vagaId) {
    }

    public record Painel(int vagasAbertas, long candidatosAtivos, long contratacoesNoMes, Double diasAteContratar,
            int conversao, Map<Etapa, Long> funil, Map<Candidato.Origem, Long> origens, List<Atencao> atencao,
            List<Atividade> atividade) {
    }

    @Transactional(readOnly = true)
    public Painel painel() {
        Instant agora = relogio.instant();
        List<Candidato> todos = candidatos.findAllByOrderByAtualizadoEmAsc();

        Map<Etapa, Long> funil = new EnumMap<>(Etapa.class);
        Map<Candidato.Origem, Long> origens = new EnumMap<>(Candidato.Origem.class);
        for (Etapa e : Etapa.values()) {
            funil.put(e, 0L);
        }
        todos.forEach(c -> {
            funil.merge(c.getEtapa(), 1L, Long::sum);
            origens.merge(c.getOrigem(), 1L, Long::sum);
        });

        List<Evento> contratacoes = eventos.contratacoes();
        long noMes = contratacoes.stream().filter(e -> e.getDataHora().isAfter(agora.minus(Duration.ofDays(30)))).count();
        OptionalDouble media = contratacoes.stream()
                .mapToLong(e -> Duration.between(e.getCandidato().getInscritoEm(), e.getDataHora()).toDays()).average();
        long ativos = todos.stream().filter(c -> c.getVaga().aberta() && !c.getEtapa().encerrada()).count();
        int conversao = todos.isEmpty() ? 0 : (int) Math.round(100.0 * funil.get(Etapa.CONTRATADO) / todos.size());

        return new Painel(recrutamento.vagasAbertas().size(), ativos, noMes,
                media.isPresent() ? Math.round(media.getAsDouble() * 10) / 10.0 : null, conversao, funil, origens,
                atencao(todos, agora), atividade());
    }

    private List<Atencao> atencao(List<Candidato> todos, Instant agora) {
        List<Atencao> itens = new ArrayList<>();
        for (Vaga vaga : recrutamento.vagasAbertas()) {
            RecrutamentoService.Funil f = recrutamento.funil(vaga.getId());
            boolean temQuemCabe = todos.stream().anyMatch(c -> c.getVaga().getId().equals(vaga.getId())
                    && c.getEtapa() == Etapa.INSCRITO && c.recomendacao().cabeNoOrcamento());
            if (f.livres() > 0 && temQuemCabe) {
                itens.add(new Atencao(TipoDeAtencao.SELECIONAR, 1, "Selecionar candidatos",
                        f.livres() + (f.livres() == 1 ? " vaga livre" : " vagas livres") + " e inscritos dentro do orçamento",
                        null, vaga.getId(), vaga.getTitulo(), 0));
            }
        }
        for (Candidato c : todos) {
            if (!c.getVaga().aberta()) {
                continue;
            }
            long dias = Duration.between(c.getAtualizadoEm(), agora).toDays();
            Atencao item = switch (c.getEtapa()) {
                case SELECIONADO -> c.getTentativasDeContato() == 0
                        ? atencao(TipoDeAtencao.LIGAR, 1, "Ligar pela primeira vez", "Selecionado e ainda sem ligação", c, dias)
                        : atencao(TipoDeAtencao.TENTAR_DE_NOVO, 2, "Tentar contato de novo",
                                c.getTentativasDeContato() + "/" + Candidato.MAXIMO_DE_TENTATIVAS + " ligações sem resposta", c, dias);
                case ENTREVISTA -> c.getNotaEntrevista() == null
                        ? atencao(TipoDeAtencao.AVALIAR, 2, "Registrar a avaliação", "Entrevista sem nota e parecer", c, dias)
                        : c.getNotaEntrevista() >= Candidato.NOTA_MINIMA_PARA_PROPOSTA
                                ? atencao(TipoDeAtencao.ENVIAR_PROPOSTA, 1, "Enviar proposta",
                                        "Entrevista com nota " + c.getNotaEntrevista() + "/5", c, dias)
                                : atencao(TipoDeAtencao.REVER_AVALIACAO, 3, "Reprovar ou reavaliar",
                                        "Nota " + c.getNotaEntrevista() + "/5, abaixo do mínimo para proposta", c, dias);
                case PROPOSTA -> dias >= DIAS_PARA_COBRAR_PROPOSTA
                        ? atencao(TipoDeAtencao.COBRAR_PROPOSTA, 2, "Cobrar resposta da proposta",
                                "Sem resposta há " + dias + " dias", c, dias)
                        : null;
                default -> null;
            };
            if (item != null) {
                itens.add(item);
            }
        }
        itens.sort(Comparator.comparingInt(Atencao::prioridade).thenComparing(Comparator.comparingLong(Atencao::dias).reversed()));
        return itens;
    }

    private static Atencao atencao(TipoDeAtencao tipo, int prioridade, String titulo, String detalhe, Candidato c, long dias) {
        return new Atencao(tipo, prioridade, titulo + ": " + c.getNome(), detalhe, c.getId(), c.getVaga().getId(),
                c.getVaga().getTitulo(), dias);
    }

    private List<Atividade> atividade() {
        return eventos.recentes(PageRequest.of(0, 12)).stream()
                .map(e -> new Atividade(e.getDataHora(), e.getTipo().name(), e.getDescricao(), e.getCandidato().getNome(),
                        e.getCandidato().getId(), e.getCandidato().getVaga().getTitulo(), e.getCandidato().getVaga().getId()))
                .toList();
    }
}
