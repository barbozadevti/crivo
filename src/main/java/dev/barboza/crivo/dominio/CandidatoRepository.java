package dev.barboza.crivo.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

    /** Em ordem de inscrição: é a ordem da fila na seleção. */
    List<Candidato> findByVagaIdOrderByInscritoEmAscIdAsc(Long vagaId);

    boolean existsByVagaIdAndEmail(Long vagaId, String email);
}
