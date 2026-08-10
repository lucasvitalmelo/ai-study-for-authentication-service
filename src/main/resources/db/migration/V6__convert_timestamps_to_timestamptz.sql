-- As colunas TIMESTAMP (sem timezone) abaixo guardavam os digitos em UTC
-- (e assim eram lidas de volta pelo Hibernate, de forma internamente
-- consistente), mas qualquer leitura fora da aplicacao (JDBC cru, query
-- manual, dashboard) reinterpreta os mesmos digitos usando o fuso local
-- da sessao, produzindo um instante deslocado (issue #20). TIMESTAMPTZ
-- guarda o instante de forma nao ambigua, independente de quem le.
--
-- "AT TIME ZONE 'UTC'" na conversao diz ao Postgres para interpretar os
-- valores ja gravados como UTC (o que de fato sao), em vez de usar o
-- fuso da sessao — sem isso, a migration replicaria o mesmo desvio nos
-- dados existentes.

ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE refresh_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE password_reset_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
