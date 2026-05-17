create table excel_import_job (
  id bigint primary key auto_increment,
  datasource_id bigint not null,
  table_id bigint,
  original_file_name varchar(255) not null,
  physical_table_name varchar(128) not null,
  display_table_name varchar(255) not null,
  row_count int not null default 0,
  column_count int not null default 0,
  status varchar(32) not null,
  error_message text,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  constraint uk_excel_import_physical_table unique (physical_table_name),
  constraint fk_excel_import_datasource foreign key (datasource_id) references datasource_config(id),
  constraint fk_excel_import_table foreign key (table_id) references datasource_table(id) on delete set null
);

