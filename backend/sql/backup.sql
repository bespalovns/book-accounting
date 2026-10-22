--
-- PostgreSQL database dump
--

\restrict bFBAVEK4cffKAXFfOC1XIHskfKeNDT0Gc0RfrbBi2fWwMadzKTPiKI6gxKpDMkd

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: authors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.authors (
    id integer NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    CONSTRAINT authors_first_name_check CHECK ((length(btrim((first_name)::text)) > 0)),
    CONSTRAINT authors_last_name_check CHECK ((length(btrim((last_name)::text)) > 0))
);


--
-- Name: authors_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.authors ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.authors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: books; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.books (
    id integer NOT NULL,
    author_id integer NOT NULL,
    publisher_id integer NOT NULL,
    title character varying(250) NOT NULL,
    code character varying(13) NOT NULL,
    year_publish integer NOT NULL,
    count_page integer NOT NULL,
    price numeric(10,2) NOT NULL,
    hardcover boolean DEFAULT false NOT NULL,
    abstract text DEFAULT ''::text NOT NULL,
    status boolean DEFAULT true NOT NULL,
    CONSTRAINT books_code_check CHECK (((code)::text ~ '^([0-9]{13}|[0-9]{9}[0-9X])$'::text)),
    CONSTRAINT books_count_page_check CHECK ((count_page > 0)),
    CONSTRAINT books_price_check CHECK ((price >= (0)::numeric)),
    CONSTRAINT books_title_check CHECK ((length(btrim((title)::text)) > 0)),
    CONSTRAINT books_year_publish_check CHECK (((year_publish >= 1) AND (year_publish <= 9999)))
);


--
-- Name: books_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.books ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.books_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: publishers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.publishers (
    id integer NOT NULL,
    name_publisher character varying(200) NOT NULL,
    address character varying(300) NOT NULL,
    site character varying(300),
    CONSTRAINT publishers_address_check CHECK ((length(btrim((address)::text)) > 0)),
    CONSTRAINT publishers_name_publisher_check CHECK ((length(btrim((name_publisher)::text)) > 0))
);


--
-- Name: publishers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.publishers ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.publishers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Data for Name: authors; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.authors (id, first_name, last_name) FROM stdin;
1	Александр	Пушкин
2	Лев	Толстой
3	Анна	Соколова
4	Иван	Морозов
5	Мария	Орлова
\.


--
-- Data for Name: books; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.books (id, author_id, publisher_id, title, code, year_publish, count_page, price, hardcover, abstract, status) FROM stdin;
2	4	3	Введение в базы данных	9780000000026	2024	240	650.00	f	Демонстрационная запись для учебного проекта.	t
3	3	2	Веб-приложения на Flask	9780000000033	2026	280	1100.00	t	Демонстрационная запись для учебного проекта.	f
1	3	2	Основы Python	9780000000019	2025	320	950.00	t	Демонстрационная запись для учебного проекта.	t
\.


--
-- Data for Name: publishers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.publishers (id, name_publisher, address, site) FROM stdin;
2	Учебное издательство	Москва, Учебная улица, 1	https://example.com
3	Книжная мастерская	Казань, Книжная улица, 2	\N
\.


--
-- Name: authors_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.authors_id_seq', 5, true);


--
-- Name: books_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.books_id_seq', 3, true);


--
-- Name: publishers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.publishers_id_seq', 3, true);


--
-- Name: authors authors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authors
    ADD CONSTRAINT authors_pkey PRIMARY KEY (id);


--
-- Name: books books_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.books
    ADD CONSTRAINT books_code_key UNIQUE (code);


--
-- Name: books books_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.books
    ADD CONSTRAINT books_pkey PRIMARY KEY (id);


--
-- Name: publishers publishers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.publishers
    ADD CONSTRAINT publishers_pkey PRIMARY KEY (id);


--
-- Name: idx_books_author_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_books_author_id ON public.books USING btree (author_id);


--
-- Name: idx_books_publisher_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_books_publisher_id ON public.books USING btree (publisher_id);


--
-- Name: books books_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.books
    ADD CONSTRAINT books_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.authors(id) ON DELETE RESTRICT;


--
-- Name: books books_publisher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.books
    ADD CONSTRAINT books_publisher_id_fkey FOREIGN KEY (publisher_id) REFERENCES public.publishers(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict bFBAVEK4cffKAXFfOC1XIHskfKeNDT0Gc0RfrbBi2fWwMadzKTPiKI6gxKpDMkd

