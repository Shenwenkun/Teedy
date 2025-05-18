insert into T_CONFIG(CFG_ID_C, CFG_VALUE_C) values('INBOX_AUTOMATIC_TAGS', 'false');
insert into T_CONFIG(CFG_ID_C, CFG_VALUE_C) values('INBOX_DELETE_IMPORTED', 'false');

create cached table T_USER_REGISTRATION_REQUEST (URR_ID_C varchar(36) not null,URR_USERNAME_C varchar(50) not null, URR_EMAIL_C varchar(100) not null, URR_STATUS_C varchar(20) not null default 'PENDING', URR_REASON_C varchar(4000), URR_CREATEDATE_D datetime not null, URR_UPDATEDATE_D datetime, URR_DELETEDATE_D datetime, primary key (URR_ID_C));

-- 添加索引以提高查询性能
create index IDX_URR_STATUS_C on T_USER_REGISTRATION_REQUEST (URR_STATUS_C);
create index IDX_URR_CREATEDATE_D on T_USER_REGISTRATION_REQUEST (URR_CREATEDATE_D);


update T_CONFIG set CFG_VALUE_C = '25' where CFG_ID_C = 'DB_VERSION';
