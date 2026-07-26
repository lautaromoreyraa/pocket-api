-- =============================================================================
--  V2 — Carga de las categorías fijas (RF-34, RF-35)
--  La lista no es editable por el usuario. "Otros" es el fallback.
-- =============================================================================

INSERT INTO categoria (nombre, icono, color, orden) VALUES
    ('Supermercado',           'shopping-cart',  '#A8BE7B',  1),
    ('Delivery/Restaurantes',  'utensils',       '#C4675A',  2),
    ('Transporte',             'bus',            '#7FB2A6',  3),
    ('Combustible',            'fuel',           '#D9A441',  4),
    ('Servicios',              'plug',           '#8F887A',  5),
    ('Suscripciones',          'repeat',         '#A79BD4',  6),
    ('Salud/Farmacia',         'heart-pulse',    '#CC8FA0',  7),
    ('Entretenimiento',        'gamepad',        '#8BA5C9',  8),
    ('Ropa/Indumentaria',      'shirt',          '#C9A88B',  9),
    ('Hogar',                  'house',          '#9BAF9B', 10),
    ('Educación',              'book',           '#B4A57F', 11),
    ('Mascotas',               'paw-print',      '#C1A0B8', 12),
    ('Otros',                  'circle-dashed',  '#665F52', 13);
