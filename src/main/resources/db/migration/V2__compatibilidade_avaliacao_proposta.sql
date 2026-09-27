-- Versão 2: requisitos e modelo de trabalho da vaga; habilidades, origem, avaliação e proposta do candidato.
-- Uma coluna por comando, para funcionar igual no H2 e no PostgreSQL.

ALTER TABLE vaga ADD COLUMN requisitos VARCHAR(300);
ALTER TABLE vaga ADD COLUMN modelo VARCHAR(12) DEFAULT 'HIBRIDO' NOT NULL;
ALTER TABLE vaga ADD COLUMN local VARCHAR(60);

ALTER TABLE candidato ADD COLUMN habilidades VARCHAR(300);
ALTER TABLE candidato ADD COLUMN linkedin VARCHAR(200);
ALTER TABLE candidato ADD COLUMN origem VARCHAR(12) DEFAULT 'MANUAL' NOT NULL;
ALTER TABLE candidato ADD COLUMN nota_entrevista INTEGER;
ALTER TABLE candidato ADD COLUMN parecer VARCHAR(500);
ALTER TABLE candidato ADD COLUMN salario_ofertado NUMERIC(12,2);

-- Notas do recrutador e pareceres cabem no histórico.
ALTER TABLE evento ALTER COLUMN descricao SET DATA TYPE VARCHAR(500);
CREATE INDEX ix_evento_data ON evento (data_hora);
