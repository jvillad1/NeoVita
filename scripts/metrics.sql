-- NeoVita usage metrics over the `events` table (see EventsTable.kt). Read-only.
-- Run:  railway connect Postgres < scripts/metrics.sql
-- Days/weeks are Colombia time. `events.created_at` is epoch milliseconds.

CREATE TEMP VIEW ev AS
SELECT user_id, type,
       (to_timestamp(created_at / 1000.0) AT TIME ZONE 'America/Bogota') AS ts
FROM events;

\echo '== 1. Eventos por tipo =='
SELECT type, count(*) AS eventos, count(DISTINCT user_id) AS usuarios,
       min(ts)::date AS desde, max(ts)::date AS hasta
FROM ev GROUP BY type ORDER BY type;

\echo '== 2. Activacion (embudo por usuario registrado) =='
SELECT count(*)                                                        AS registrados,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM ev WHERE ev.user_id = u.id AND ev.type = 'session_start')) AS abrieron_app,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM assessments a WHERE a.user_id = u.id))                     AS completaron_evaluacion,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM ev WHERE ev.user_id = u.id AND ev.type = 'chat_message_sent')) AS usaron_coach
FROM users u;

\echo '== 3. Retencion semanal (cohorte = semana de su primer session_start) =='
WITH first_seen AS (
  SELECT user_id, date_trunc('week', min(ts)) AS cohorte
  FROM ev WHERE type = 'session_start' GROUP BY user_id
), weeks AS (
  SELECT DISTINCT user_id, date_trunc('week', ts) AS semana
  FROM ev WHERE type = 'session_start'
)
SELECT f.cohorte::date AS semana_cohorte,
       count(DISTINCT f.user_id) AS usuarios,
       count(DISTINCT w.user_id) FILTER (WHERE w.semana = f.cohorte + interval '1 week') AS vuelven_semana_1,
       count(DISTINCT w.user_id) FILTER (WHERE w.semana = f.cohorte + interval '2 weeks') AS vuelven_semana_2
FROM first_seen f LEFT JOIN weeks w USING (user_id)
GROUP BY f.cohorte ORDER BY f.cohorte;

\echo '== 4. Retencion dia 7 (vuelve entre el dia 1 y el 7 tras su primera sesion) =='
WITH first_seen AS (
  SELECT user_id, min(ts) AS primera FROM ev WHERE type = 'session_start' GROUP BY user_id
)
SELECT count(*) AS usuarios,
       count(*) FILTER (WHERE now() - primera >= interval '7 days') AS con_7_dias_de_historia,
       count(*) FILTER (WHERE now() - primera >= interval '7 days' AND EXISTS (
         SELECT 1 FROM ev e WHERE e.user_id = f.user_id AND e.type = 'session_start'
           AND e.ts::date > f.primera::date AND e.ts::date <= f.primera::date + 7)) AS volvieron_en_7_dias
FROM first_seen f;

\echo '== 5. Coach: mensajes por usuario y semana =='
SELECT date_trunc('week', ts)::date AS semana, user_id, count(*) AS mensajes
FROM ev WHERE type = 'chat_message_sent'
GROUP BY 1, 2 ORDER BY 1, 3 DESC;
