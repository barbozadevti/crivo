package dev.barboza.crivo.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.crivo.config.Demonstracao;
import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Evento;
import dev.barboza.crivo.dominio.ResultadoDoContato;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.Funil;
import dev.barboza.crivo.servico.SimulacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api")
@Tag(name = "Recrutamento")
public class RecrutamentoController {

    private final RecrutamentoService recrutamento;
    private final SimulacaoService simulacao;
    private final Demonstracao demonstracao;

    public RecrutamentoController(RecrutamentoService recrutamento, SimulacaoService simulacao, Demonstracao demonstracao) {
        this.recrutamento = recrutamento;
        this.simulacao = simulacao;
        this.demonstracao = demonstracao;
    }

    // ---------- Contratos ----------

    public record EtapaResposta(String etapa, String nome, boolean ocupaVaga, boolean encerrada) {
        static EtapaResposta de(Etapa e) {
            return new EtapaResposta(e.name(), e.nome(), e.ocupaVaga(), e.encerrada());
        }
    }

    public record VagaResposta(Long id, String titulo, String area, String descricao, BigDecimal salarioBase, int quantidade,
            String status, Instant criadaEm, Instant encerradaEm, long total, int ocupadas, int livres, long contratados,
            int acimaDoOrcamento, Map<String, Long> porEtapa) {

        static VagaResposta de(Funil f) {
            Map<String, Long> etapas = new LinkedHashMap<>();
            f.porEtapa().forEach((e, n) -> etapas.put(e.name(), n));
            var v = f.vaga();
            return new VagaResposta(v.getId(), v.getTitulo(), v.getArea(), v.getDescricao(), v.getSalarioBase(),
                    v.getQuantidade(), v.getStatus().name(), v.getCriadaEm(), v.getEncerradaEm(), f.total(), f.ocupadas(),
                    f.livres(), f.contratados(), f.acimaDoOrcamento(), etapas);
        }
    }

    public record CandidatoResposta(Long id, Long vagaId, String nome, String email, String telefone,
            BigDecimal salarioPretendido, String etapa, String etapaNome, String recomendacao, String recomendacaoTexto,
            int tentativasDeContato, Instant inscritoEm, List<EtapaResposta> proximas) {

        static CandidatoResposta de(Candidato c) {
            List<EtapaResposta> proximas = c.getEtapa().proximas().stream()
                    .filter(e -> e != Etapa.SEM_CONTATO).map(EtapaResposta::de).toList();
            return new CandidatoResposta(c.getId(), c.getVaga().getId(), c.getNome(), c.getEmail(), c.getTelefone(),
                    c.getSalarioPretendido(), c.getEtapa().name(), c.getEtapa().nome(), c.recomendacao().name(),
                    c.recomendacao().texto(), c.getTentativasDeContato(), c.getInscritoEm(), proximas);
        }
    }

    public record EventoResposta(Instant dataHora, String tipo, String descricao) {
        static EventoResposta de(Evento e) {
            return new EventoResposta(e.getDataHora(), e.getTipo().name(), e.getDescricao());
        }
    }

    public record DetalheDoCandidato(CandidatoResposta candidato, List<EventoResposta> historico) {
    }

    public record NovaVagaRequisicao(String titulo, String area, String descricao, BigDecimal salarioBase, Integer quantidade) {
    }

    public record NovoCandidatoRequisicao(String nome, String email, String telefone, BigDecimal salarioPretendido) {
    }

    public record Movimento(@NotNull(message = "Informe a etapa de destino.") Etapa etapa, String motivo) {
    }

    public record Contato(@NotNull(message = "Informe se o candidato atendeu.") Boolean atendeu) {
    }

    public record ContatoResposta(String resultado, String mensagem, CandidatoResposta candidato, CandidatoResposta suplente) {
    }

    public record ImportacaoRequisicao(String texto) {
    }

    // ---------- Rotas ----------

    @Operation(summary = "Etapas do processo, na ordem do quadro")
    @GetMapping("/etapas")
    public List<EtapaResposta> etapas() {
        return Arrays.stream(Etapa.values()).map(EtapaResposta::de).toList();
    }

    @GetMapping("/vagas")
    public List<VagaResposta> vagas() {
        return recrutamento.vagas().stream().map(VagaResposta::de).toList();
    }

    @PostMapping("/vagas")
    public ResponseEntity<VagaResposta> criarVaga(@RequestBody NovaVagaRequisicao r) {
        var vaga = recrutamento.criarVaga(new RecrutamentoService.NovaVaga(r.titulo(), r.area(), r.descricao(),
                r.salarioBase(), r.quantidade()));
        return ResponseEntity.status(HttpStatus.CREATED).body(VagaResposta.de(recrutamento.funil(vaga.getId())));
    }

    @GetMapping("/vagas/{id}")
    public VagaResposta vaga(@PathVariable Long id) {
        return VagaResposta.de(recrutamento.funil(id));
    }

    @PostMapping("/vagas/{id}/encerramento")
    public VagaResposta encerrar(@PathVariable Long id) {
        recrutamento.encerrarVaga(id);
        return VagaResposta.de(recrutamento.funil(id));
    }

    @GetMapping("/vagas/{id}/candidatos")
    public List<CandidatoResposta> candidatos(@PathVariable Long id) {
        return recrutamento.candidatosDa(id).stream().map(CandidatoResposta::de).toList();
    }

    @Operation(summary = "Inscrever candidato", description = "A triagem pelo orçamento da vaga é feita na hora.")
    @PostMapping("/vagas/{id}/candidatos")
    public ResponseEntity<CandidatoResposta> inscrever(@PathVariable Long id, @RequestBody NovoCandidatoRequisicao r) {
        Candidato c = recrutamento.inscrever(id, new RecrutamentoService.NovoCandidato(r.nome(), r.email(), r.telefone(),
                r.salarioPretendido()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CandidatoResposta.de(c));
    }

    @Operation(summary = "Importar candidatos", description = "Uma linha por candidato: nome;e-mail;telefone;salário.")
    @PostMapping("/vagas/{id}/importacao")
    public RecrutamentoService.Importacao importar(@PathVariable Long id, @RequestBody ImportacaoRequisicao r) {
        return recrutamento.importar(id, r.texto());
    }

    @Operation(summary = "Selecionar", description = "Seleciona, em ordem de inscrição, quem cabe no orçamento até completar as vagas.")
    @PostMapping("/vagas/{id}/selecao")
    public List<CandidatoResposta> selecionar(@PathVariable Long id) {
        return recrutamento.selecionar(id).stream().map(CandidatoResposta::de).toList();
    }

    @Operation(summary = "Simular o processo", description = "Sorteia contatos, entrevistas e propostas. A mesma semente dá o mesmo resultado.")
    @PostMapping("/vagas/{id}/simulacao")
    public SimulacaoService.Simulacao simular(@PathVariable Long id, @RequestParam(required = false) Long semente) {
        return simulacao.simular(id, semente);
    }

    @GetMapping("/candidatos/{id}")
    public DetalheDoCandidato candidato(@PathVariable Long id) {
        return new DetalheDoCandidato(CandidatoResposta.de(recrutamento.candidato(id)),
                recrutamento.historico(id).stream().map(EventoResposta::de).toList());
    }

    @Operation(summary = "Mudar de etapa", description = "Validado pela máquina de estados: mudança não permitida devolve 409.")
    @PostMapping("/candidatos/{id}/etapa")
    public CandidatoResposta mover(@PathVariable Long id, @Valid @RequestBody Movimento m) {
        return CandidatoResposta.de(recrutamento.mover(id, m.etapa(), m.motivo()));
    }

    @Operation(summary = "Registrar tentativa de contato", description = "Na 3ª sem sucesso, o candidato sai e o suplente é chamado.")
    @PostMapping("/candidatos/{id}/contatos")
    public ContatoResposta contato(@PathVariable Long id, @Valid @RequestBody Contato c) {
        ResultadoDoContato resultado = recrutamento.registrarContato(id, c.atendeu());
        return switch (resultado) {
            case ResultadoDoContato.Atendeu a -> new ContatoResposta("ATENDEU",
                    a.candidato().getNome() + " atendeu na " + a.tentativa() + "ª tentativa e segue para a entrevista.",
                    CandidatoResposta.de(a.candidato()), null);
            case ResultadoDoContato.NaoAtendeu n -> new ContatoResposta("NAO_ATENDEU",
                    n.candidato().getNome() + " não atendeu. Restam " + n.restantes() + " tentativa(s).",
                    CandidatoResposta.de(n.candidato()), null);
            case ResultadoDoContato.SemContato s -> new ContatoResposta("SEM_CONTATO",
                    "Sem contato com " + s.candidato().getNome() + " após " + Candidato.MAXIMO_DE_TENTATIVAS + " tentativas."
                            + (s.suplente() != null ? " Suplente chamado: " + s.suplente().getNome() + "." : " Não há suplente na fila."),
                    CandidatoResposta.de(s.candidato()), s.suplente() == null ? null : CandidatoResposta.de(s.suplente()));
        };
    }

    @Operation(summary = "Reiniciar demonstração", description = "Apaga tudo e recarrega as vagas de exemplo.")
    @PostMapping("/demonstracao/reinicio")
    public ResponseEntity<Void> reiniciar() {
        demonstracao.reiniciar();
        return ResponseEntity.noContent().build();
    }
}
