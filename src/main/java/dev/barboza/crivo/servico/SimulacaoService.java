package dev.barboza.crivo.servico;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Dinheiro;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.ResultadoDoContato;

/**
 * Roda o processo seletivo inteiro de uma vaga, sorteando quem atende o telefone (1 em 3, como
 * nas aulas), quem passa na entrevista (70%) e quem aceita a proposta (80%). Com a mesma
 * semente, o resultado é sempre o mesmo.
 */
@Service
public class SimulacaoService {

    private static final int LIMITE_DE_RODADAS = 200;

    private final RecrutamentoService recrutamento;
    private final java.time.Clock relogio;

    public SimulacaoService(RecrutamentoService recrutamento, java.time.Clock relogio) {
        this.recrutamento = recrutamento;
        this.relogio = relogio;
    }

    public record Simulacao(long semente, List<String> passos, RecrutamentoService.Funil funil) {
    }

    @Transactional
    public Simulacao simular(Long vagaId, Long semente) {
        long usada = semente != null ? semente : System.nanoTime() % 100000;
        Random sorteio = new Random(usada);
        List<String> passos = new ArrayList<>();

        List<Candidato> selecionados = recrutamento.selecionarEm(vagaId, relogio.instant());
        selecionados.forEach(c -> passos.add("Selecionado: " + c.getNome()
                + " (pretende " + Dinheiro.formatar(c.getSalarioPretendido()) + ")"));
        if (selecionados.isEmpty()) {
            passos.add("Nenhum novo candidato selecionado: as vagas estão ocupadas ou ninguém cabe no orçamento.");
        }

        for (int rodada = 0; rodada < LIMITE_DE_RODADAS; rodada++) {
            List<Candidato> ativos = recrutamento.candidatosDa(vagaId).stream()
                    .filter(c -> c.getEtapa().ocupaVaga() && c.getEtapa() != Etapa.CONTRATADO).toList();
            if (ativos.isEmpty() || !recrutamento.funil(vagaId).vaga().aberta()) {
                break;
            }
            for (Candidato c : ativos) {
                Candidato atual = recrutamento.candidato(c.getId());
                if (!atual.getVaga().aberta()) {
                    break;
                }
                passos.add(passo(atual, sorteio));
            }
        }

        RecrutamentoService.Funil funil = recrutamento.funil(vagaId);
        passos.add("Resultado: " + funil.contratados() + " de " + funil.vaga().getQuantidade() + " vaga(s) preenchida(s)"
                + (funil.vaga().aberta() ? "; a vaga continua aberta." : "; vaga encerrada."));
        return new Simulacao(usada, passos, funil);
    }

    private String passo(Candidato c, Random sorteio) {
        return switch (c.getEtapa()) {
            case SELECIONADO -> descrever(recrutamento.registrarContato(c.getId(), sorteio.nextInt(3) == 1));
            case ENTREVISTA -> entrevistar(c, sorteio);
            case PROPOSTA -> sorteio.nextInt(10) < 8
                    ? mover(c, Etapa.CONTRATADO, "aceitou a proposta", "aceitou a proposta: contratado")
                    : mover(c, Etapa.DESISTIU, "recusou a proposta", "recusou a proposta");
            default -> c.getNome() + ": nada a fazer";
        };
    }

    /** 70% passam: nota de 3 a 5 e proposta pelo menor valor entre a pretensão e o orçamento. */
    private String entrevistar(Candidato c, Random sorteio) {
        if (sorteio.nextInt(10) < 7) {
            int nota = 3 + sorteio.nextInt(3);
            recrutamento.avaliar(c.getId(), nota, "Simulação: boa entrevista técnica e comportamental.");
            recrutamento.enviarProposta(c.getId(), c.getSalarioPretendido().min(c.getVaga().getSalarioBase()));
            return c.getNome() + " passou na entrevista (nota " + nota + "/5) e recebeu proposta";
        }
        recrutamento.avaliar(c.getId(), 1 + sorteio.nextInt(2), "Simulação: não atendeu aos requisitos técnicos.");
        return mover(c, Etapa.REPROVADO, "não passou na entrevista", "não passou na entrevista");
    }

    private String mover(Candidato c, Etapa destino, String motivo, String texto) {
        recrutamento.mover(c.getId(), destino, motivo);
        return c.getNome() + " " + texto;
    }

    /** O {@code switch} sobre a interface selada cobre todos os desfechos possíveis. */
    static String descrever(ResultadoDoContato resultado) {
        return switch (resultado) {
            case ResultadoDoContato.Atendeu a ->
                    "Conseguimos contato com " + a.candidato().getNome() + " na " + a.tentativa() + "ª tentativa";
            case ResultadoDoContato.NaoAtendeu n ->
                    n.candidato().getNome() + " não atendeu (" + n.tentativa() + "ª tentativa, restam " + n.restantes() + ")";
            case ResultadoDoContato.SemContato s ->
                    "Não conseguimos contato com " + s.candidato().getNome() + " após " + Candidato.MAXIMO_DE_TENTATIVAS
                            + " tentativas" + (s.suplente() != null ? "; suplente chamado: " + s.suplente().getNome() : "");
        };
    }
}
