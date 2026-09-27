package dev.barboza.crivo.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VagaRepository extends JpaRepository<Vaga, Long> {

    List<Vaga> findAllByOrderByStatusAscCriadaEmDesc();
}
