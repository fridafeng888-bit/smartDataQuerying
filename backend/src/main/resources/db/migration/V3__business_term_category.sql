set @add_category_column = (
  select if(
    exists(
      select 1
      from information_schema.columns
      where table_schema = database()
        and table_name = 'business_term'
        and column_name = 'category'
    ),
    'select 1',
    'alter table business_term add column category varchar(64) not null default ''通用'' after name'
  )
);

prepare add_category_column_stmt from @add_category_column;
execute add_category_column_stmt;
deallocate prepare add_category_column_stmt;

set @add_category_index = (
  select if(
    exists(
      select 1
      from information_schema.statistics
      where table_schema = database()
        and table_name = 'business_term'
        and index_name = 'idx_business_term_category_enabled'
    ),
    'select 1',
    'create index idx_business_term_category_enabled on business_term(category, enabled)'
  )
);

prepare add_category_index_stmt from @add_category_index;
execute add_category_index_stmt;
deallocate prepare add_category_index_stmt;
