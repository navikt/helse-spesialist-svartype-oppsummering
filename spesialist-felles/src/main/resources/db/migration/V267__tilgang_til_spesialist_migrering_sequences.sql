DO $$BEGIN
    IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'spesialist-migrering')
    THEN GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "spesialist-migrering";
    END IF;
END$$;
