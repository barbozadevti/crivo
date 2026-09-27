package dev.barboza.crivo.dominio;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByCandidatoIdOrderByDataHoraAscIdAsc(Long candidatoId);

    /** Atividade recente de todas as vagas, já com candidato e vaga carregados. */
    @Query("select e from Evento e join fetch e.candidato c join fetch c.vaga order by e.dataHora desc, e.id desc")
    List<Evento> recentes(Pageable limite);

    /** Contratações, para o tempo médio até a contratação. */
    @Query("select e from Evento e join fetch e.candidato c where e.etapaNova = dev.barboza.crivo.dominio.Etapa.CONTRATADO")
    List<Evento> contratacoes();
}
