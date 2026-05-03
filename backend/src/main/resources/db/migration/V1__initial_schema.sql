create table datasource_config (
  id bigint primary key auto_increment,
  name varchar(128) not null,
  type varchar(32) not null,
  host varchar(255) not null,
  port int not null,
  database_name varchar(128) not null,
  username varchar(128) not null,
  encrypted_password text not null,
  ssl_enabled boolean not null default false,
  enabled boolean not null default true,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table datasource_table (
  id bigint primary key auto_increment,
  datasource_id bigint not null,
  schema_name varchar(128),
  table_name varchar(128) not null,
  comment_text text,
  enabled boolean not null default true,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  constraint fk_table_datasource foreign key (datasource_id) references datasource_config(id) on delete cascade
);

create table datasource_column (
  id bigint primary key auto_increment,
  table_id bigint not null,
  column_name varchar(128) not null,
  data_type varchar(128) not null,
  nullable_col boolean not null default true,
  comment_text text,
  sensitive_flag boolean not null default false,
  enabled boolean not null default true,
  ordinal_position int not null default 0,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  constraint fk_column_table foreign key (table_id) references datasource_table(id) on delete cascade
);

create table business_term (
  id bigint primary key auto_increment,
  name varchar(128) not null,
  synonyms text,
  definition_text text not null,
  calculation text,
  enabled boolean not null default true,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table sql_example (
  id bigint primary key auto_increment,
  question text not null,
  sql_text text not null,
  description_text text,
  datasource_id bigint,
  enabled boolean not null default true,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  constraint fk_example_datasource foreign key (datasource_id) references datasource_config(id) on delete set null
);

create table chat_session (
  id bigint primary key auto_increment,
  title varchar(255) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table chat_message (
  id bigint primary key auto_increment,
  session_id bigint not null,
  role varchar(32) not null,
  content_text text not null,
  generated_sql text,
  explanation text,
  error_message text,
  created_at timestamp not null default current_timestamp,
  constraint fk_message_session foreign key (session_id) references chat_session(id) on delete cascade
);

create table query_execution (
  id bigint primary key auto_increment,
  datasource_id bigint not null,
  session_id bigint,
  question text not null,
  generated_sql text,
  status varchar(32) not null,
  duration_ms bigint,
  row_count int,
  error_message text,
  created_at timestamp not null default current_timestamp,
  constraint fk_execution_datasource foreign key (datasource_id) references datasource_config(id),
  constraint fk_execution_session foreign key (session_id) references chat_session(id) on delete set null
);

create table llm_config (
  id bigint primary key auto_increment,
  base_url varchar(512) not null,
  model varchar(128) not null,
  encrypted_api_key text,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);
