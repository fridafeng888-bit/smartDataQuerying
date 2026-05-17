alter table business_term
  add column term_type varchar(32) not null default 'DIMENSION' after category,
  add column domain varchar(64) not null default '销售商机' after term_type,
  add column aliases text after synonyms,
  add column business_rules text after calculation,
  add column priority int not null default 50 after business_rules,
  add column owner varchar(128) after priority,
  add column verified boolean not null default false after owner;

update business_term
set domain = case
    when category is null or category = '' or category = '通用' then '销售商机'
    else category
  end,
  aliases = coalesce(aliases, synonyms),
  term_type = case
    when term_type in ('METRIC', 'DIMENSION', 'ORG') then term_type
    else 'DIMENSION'
  end;

create index idx_business_term_domain_type_enabled on business_term(domain, term_type, enabled);
create index idx_business_term_priority on business_term(priority);

create table business_term_binding (
  id bigint primary key auto_increment,
  term_id bigint not null,
  datasource_id bigint,
  table_name varchar(128),
  column_name varchar(128),
  field_role varchar(64),
  filter_expression text,
  value_mappings text,
  priority int not null default 50,
  enabled boolean not null default true,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  constraint fk_term_binding_term foreign key (term_id) references business_term(id) on delete cascade,
  constraint fk_term_binding_datasource foreign key (datasource_id) references datasource_config(id) on delete set null
);

create index idx_term_binding_term_enabled on business_term_binding(term_id, enabled);
create index idx_term_binding_datasource_table on business_term_binding(datasource_id, table_name);
