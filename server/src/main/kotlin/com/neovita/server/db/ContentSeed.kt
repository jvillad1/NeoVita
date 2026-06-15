package com.neovita.server.db

import com.neovita.shared.network.dto.ContentItemDto

/**
 * Initial dashboard content, migrated verbatim from the hard-coded list that used to live in
 * the client's DashboardViewModel. Seeded on first boot; afterwards it's editable via the
 * /api/content CRUD endpoints (the DB is the source of truth).
 */
val SEED_CONTENT: List<ContentItemDto> = listOf(
    ContentItemDto("n1", "Los 5 alimentos antiinflamatorios que deberías comer cada semana", "NUTRITION", "ARTICLE", "La inflamación crónica es uno de los principales aceleradores del envejecimiento. Estos alimentos la combaten de forma natural.", 5, 0),
    ContentItemDto("n2", "Ayuno intermitente después de los 50: qué dice la ciencia", "NUTRITION", "ARTICLE", "No todas las ventanas de ayuno son iguales. Descubre cuál se adapta mejor a tu metabolismo a esta edad.", 7, 1),
    ContentItemDto("n3", "Proteína después de los 40: por qué necesitas más de lo que crees", "NUTRITION", "TIP", "La sarcopenia comienza antes de lo esperado. Ajustar tu ingesta proteica es la intervención más sencilla y efectiva.", 3, 2),
    ContentItemDto("e1", "Zona 2: el entrenamiento que los longevos hacen diferente", "EXERCISE", "ARTICLE", "El cardio de baja intensidad sostenida activa mecanismos mitocondriales que el ejercicio intenso no puede replicar.", 6, 3),
    ContentItemDto("e2", "Fuerza funcional vs. gimnasio tradicional: qué importa realmente", "EXERCISE", "ARTICLE", "La capacidad de levantarte del suelo sin apoyo predice tu mortalidad a 10 años. Te explicamos por qué.", 5, 4),
    ContentItemDto("e3", "10 minutos de movilidad cada mañana: la rutina que cambia todo", "EXERCISE", "TIP", "No es flexibilidad, es movilidad articular. Esta distinción puede evitarte años de dolor.", 3, 5),
    ContentItemDto("s1", "La arquitectura del sueño profundo: cómo recuperar tus ondas delta", "SLEEP", "ARTICLE", "El sueño de ondas lentas es donde ocurre la reparación celular. Aprende qué lo destruye y cómo protegerlo.", 8, 6),
    ContentItemDto("s2", "Temperatura, luz y ritmo circadiano: los tres reguladores que ignoras", "SLEEP", "ARTICLE", "Tu cuerpo tiene un reloj maestro. Estas señales ambientales lo sincronizan o lo desajustan.", 6, 7),
    ContentItemDto("s3", "Por qué cenar tarde arruina más que tu digestión", "SLEEP", "TIP", "El timing de la última comida afecta directamente tus ciclos de sueño profundo. La ventana óptima te sorprenderá.", 3, 8),
    ContentItemDto("m1", "Estrés crónico y envejecimiento acelerado: el vínculo que ya no se puede ignorar", "MENTAL_HEALTH", "ARTICLE", "Los telómeros se acortan con el estrés sostenido. Esto no es metáfora: es biología medible.", 7, 9),
    ContentItemDto("m2", "Coherencia cardíaca: 5 minutos al día para resetear tu sistema nervioso", "MENTAL_HEALTH", "TIP", "La variabilidad de la frecuencia cardíaca es el marcador de estrés más confiable. Esta técnica la mejora en semanas.", 4, 10),
    ContentItemDto("m3", "Propósito de vida y longevidad: lo que los estudios de centenarios revelan", "MENTAL_HEALTH", "ARTICLE", "El ikigai japonés y el moai no son filosofía: son factores protectores documentados contra la mortalidad prematura.", 9, 11),
    ContentItemDto("g1", "Los 9 hallazgos del envejecimiento biológico que ya puedes intervenir", "GENERAL", "ARTICLE", "Desde la disfunción mitocondrial hasta la senescencia celular: un mapa claro de dónde actúa la longevidad moderna.", 10, 12),
    ContentItemDto("g2", "VO2 máx: el número que predice mejor tu salud futura", "GENERAL", "ARTICLE", "Más que cualquier análisis de sangre, tu capacidad aeróbica máxima revela tu trayectoria de salud.", 6, 13),
    ContentItemDto("g3", "Las Zonas Azules: 5 hábitos universales de los pueblos más longevos", "GENERAL", "TIP", "Okinawa, Cerdeña, Nicoya. Culturas distintas, patrones sorprendentemente idénticos.", 4, 14),
)
