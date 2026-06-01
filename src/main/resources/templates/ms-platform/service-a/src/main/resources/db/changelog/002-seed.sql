INSERT INTO resources_a (name, description)
    VALUES ('ResourceA 1', 'Premiere ressource SQL') ON CONFLICT(name) DO NOTHING;
INSERT INTO resources_a (name, description)
    VALUES ('ResourceA 2', 'Deuxieme ressource SQL') ON CONFLICT(name) DO NOTHING;
