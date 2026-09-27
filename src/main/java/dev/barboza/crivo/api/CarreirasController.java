package dev.barboza.crivo.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Habilidades;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.servico.RecrutamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lado do candidato: a página "Trabalhe conosco". Só mostra vagas abertas e nunca revela
 * dados internos (triagem, orçamento exato, outros candidatos).
 */
@RestController
@RequestMapping("/api/carreiras")
@Tag(name = "Carreiras (público)")
public class CarreirasController {

    private final RecrutamentoService recrutamento;

    public CarreirasController(RecrutamentoService recrutamento) {
        this.recrutamento = recrutamento;
    }

    public record VagaPublica(Long id, String titulo, String area, String descricao, List<String> requisitos, String modelo,
            String local, int vagas) {

        static VagaPublica de(Vaga v) {
            return new VagaPublica(v.getId(), v.getTitulo(), v.getArea(), v.getDescricao(), Habilidades.lista(v.getRequisitos()),
                    v.getModelo().nome(), v.getLocal(), v.getQuantidade());
        }
    }

    public record Candidatura(String nome, String email, String telefone, BigDecimal salarioPretendido, String habilidades,
            String linkedin) {
    }

    public record Recebida(String mensagem) {
    }

    @Operation(summary = "Vagas abertas")
    @GetMapping
    public List<VagaPublica> vagas() {
        return recrutamento.vagasAbertas().stream().map(VagaPublica::de).toList();
    }

    @Operation(summary = "Candidatar-se", description = "A triagem acontece do lado do recrutador; o candidato só recebe a confirmação.")
    @PostMapping("/{vagaId}/candidaturas")
    public ResponseEntity<Recebida> candidatar(@PathVariable Long vagaId, @RequestBody Candidatura c) {
        Candidato novo = recrutamento.inscrever(vagaId, new RecrutamentoService.NovoCandidato(c.nome(), c.email(), c.telefone(),
                c.salarioPretendido(), c.habilidades(), c.linkedin(), Candidato.Origem.CARREIRAS));
        return ResponseEntity.status(HttpStatus.CREATED).body(new Recebida("Recebemos sua candidatura para "
                + novo.getVaga().getTitulo() + ", " + novo.getNome().split(" ")[0]
                + ". Nossa equipe vai analisar seu perfil e entrar em contato pelo e-mail " + novo.getEmail() + "."));
    }
}
