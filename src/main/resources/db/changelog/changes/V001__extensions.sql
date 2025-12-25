--liquibase formatted sql

--changeset system:001-extensions
--comment: Создание расширений PostgreSQL

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

