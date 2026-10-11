-- PortalDb create
alter table UP_DLM_EVALUATOR_PAREN add constraint FK_3hbkemg0gydgc606l2io1x0gp foreign key (PAREN_EVAL_ID) references UP_DLM_EVALUATOR
alter table UP_DLM_EVALUATOR_PAREN add constraint FK_b3rertpqnnjrvd257fge2sj75 foreign key (CHILD_EVAL_ID) references UP_DLM_EVALUATOR
alter table UP_DLM_EVALUATOR_PAREN add constraint UK_b3rertpqnnjrvd257fge2sj75 unique (CHILD_EVAL_ID)
alter table UP_MESSAGE add constraint UK_65m5c9buejonoq0ut61lvj3ic unique (CODE, LOCALE)
alter table UP_MUTEX add constraint UK_qj79fotsiap16odx3dtad2she unique (MUTEX_NAME)
alter table UP_PAGS_GROUP add constraint UK_oedpp3d5kyxoex9ge9ct35nw1 unique (NAME)
alter table UP_PAGS_GROUP_MEMBERS add constraint FK_561gbtgfe5oi30yc2bspcoom foreign key (PAGS_GROUP_ID) references UP_PAGS_GROUP
alter table UP_PAGS_GROUP_MEMBERS add constraint FK_fsr7qaohryhcdv877sg60id6e foreign key (PAGS_GROUP_MEMBER_ID) references UP_PAGS_GROUP
alter table UP_PAGS_TEST add constraint FK_474tvkmldo7agadpwp0c7v3ko foreign key (PAGS_TEST_GROUP_ID) references UP_PAGS_TEST_GROUP
alter table UP_PAGS_TEST_GROUP add constraint FK_hygx3nc0f1dl8tgsmvte36ggo foreign key (PAGS_GROUP_ID) references UP_PAGS_GROUP
alter table UP_PERMISSION_ACTIVITY add constraint FK_16ncixqeffipy3vhia0bksvwy foreign key (OWNER_ID) references UP_PERMISSION_OWNER
alter table UP_PERMISSION_ACTIVITY add constraint UK_11q11jd5ueijrwnxdr33vb1dp unique (ACTIVITY_FNAME)
alter table UP_PERMISSION_ACTIVITY add constraint UK_emvbet2cncdkxc1pdho9jwsjr unique (ACTIVITY_NAME)
alter table UP_PERMISSION_OWNER add constraint UK_mp27o5pynt9lms7yeuhkqk3rx unique (OWNER_FNAME)
alter table UP_PERSON_ATTR add constraint FK_4s6n2w5mggpiif98ukmoyx5f8 foreign key (USER_DIR_ID) references UP_PERSON_DIR
alter table UP_PERSON_ATTR_VALUES add constraint FK_qqexbdecsbkj4q5kiomuwn62d foreign key (ATTR_ID) references UP_PERSON_ATTR
alter table UP_PERSON_DIR add constraint UK_lx6h4h5oxdu3u1in1x5tuwmlb unique (USER_NAME)
alter table UP_PORTAL_COOKIES add constraint UK_6mo5bic5mh18wmnsixay4lte8 unique (COOKIE_VALUE)
alter table UP_PORTLET_COOKIES add constraint FK_1eiw3v16g4l8uic967eefpupv foreign key (portalCookie_PORTAL_COOKIE_ID) references UP_PORTAL_COOKIES
alter table UP_PORTLET_COOKIES add constraint FK_5nbeljrfl8xrselnib4jlc8rm foreign key (PORTAL_COOKIE_ID) references UP_PORTAL_COOKIES
alter table UP_PORTLET_COOKIES add constraint UK_b0x095hghtkdlthyahnocecsn unique (PORTAL_COOKIE_ID, COOKIE_NAME)
alter table UP_PORTLET_DEF add constraint FK_aw02n7y507j2o7a5unh3x6gmx foreign key (PORTLET_TYPE_ID) references UP_PORTLET_TYPE
alter table UP_PORTLET_DEF add constraint FK_sk16u3b6h5onabw0tkbfjb9t3 foreign key (PORTLET_PREFS_ID) references UP_PORTLET_PREFS
alter table UP_PORTLET_DEF add constraint UK_3fkifhnxe461tuvfr9lf06jkh unique (PORTLET_FNAME)
alter table UP_PORTLET_DEF add constraint UK_3lfnk29hf61sjrasap81gjimr unique (PORTLET_NAME)
alter table UP_PORTLET_DEF_MDATA add constraint FK_3mblqw8kx445e1wphecduvkal foreign key (PORTLET_ID) references UP_PORTLET_DEF
alter table UP_PORTLET_DEF_PARAM add constraint FK_8pr53vk0s7cj591svwm55mwuf foreign key (PORTLET_DEF_ID) references UP_PORTLET_DEF
alter table UP_PORTLET_ENT add constraint FK_9g5u5s6q6fyllrocmjknkgke1 foreign key (PORTLET_PREFS_ID) references UP_PORTLET_PREFS
alter table UP_PORTLET_ENT add constraint FK_momja5hs0eruu7ke7naq542hn foreign key (PORTLET_DEF_ID) references UP_PORTLET_DEF
alter table UP_PORTLET_ENT add constraint UK_p412wnejx7lpcmnk242fvi7g unique (LAYOUT_NODE_ID, USER_ID)
alter table UP_PORTLET_ENT__STATES add constraint FK_78ol5mt3uvfgxikpnhm4gc8hr foreign key (STYLESHEET_DESCRIPTOR_ID) references UP_SS_DESC
alter table UP_PORTLET_ENT__STATES add constraint FK_fdl45hs325hvdevocoynuufgd foreign key (PORTLET_ENT_ID) references UP_PORTLET_ENT
alter table UP_PORTLET_LIFECYCLE add constraint FK_h2ih95p990rtn5nu74udal0ab foreign key (PORTLET_DEF_ID) references UP_PORTLET_DEF
alter table UP_PORTLET_LIST add constraint UK_53sa73kbqlb0xe1dh2ekvoquc unique (OWNER_USERNAME, NAME)
alter table UP_PORTLET_LIST_ITEM add constraint FK_qsriu31b7uia45e0aj9odg368 foreign key (LIST_ID) references UP_PORTLET_LIST
alter table UP_PORTLET_LIST_ITEM add constraint UK_9272fgiw76nqfefv2rt2hhy9u unique (LIST_ID, LIST_ORDER, ENTITY_ID)
alter table UP_PORTLET_LIST_ITEM add constraint UK_e52akww6cs9bgpjtvvf1xkoy1 unique (LIST_ID, ENTITY_ID)
alter table UP_PORTLET_PREF add constraint FK_pgqclxrroe8ed8stf00pt2s93 foreign key (PORTLET_PREFS_ID) references UP_PORTLET_PREFS
alter table UP_PORTLET_PREF_VALUES add constraint FK_tqk3058y17ttobwa3l8pc4fwd foreign key (PORTLET_PREF_ID) references UP_PORTLET_PREF
alter table UP_PORTLET_RATING add constraint FK_m8902ol9lpgf17b081uvfobyl foreign key (PORTLET_ID) references UP_PORTLET_DEF
alter table UP_PORTLET_TYPE add constraint UK_5pdec3and8u5oaoymoo404lba unique (TYPE_NAME)
alter table UP_PROFILE_SELECTION add constraint UK_q17d2ct8ag13hwtwjsnci8pi3 unique (USERNAME)
alter table UP_SS_DESC add constraint UK_9ncctlknsd49yatplkxt6xohf unique (SS_NAME)
alter table UP_SS_DESC_LAY_ATTR add constraint FK_meru2advdfypwhfddn5gnjnvy foreign key (SS_DESC_ID) references UP_SS_DESC
alter table UP_SS_DESC_LAY_ATTR_ELMS add constraint FK_2qvk7f3048lo65b5wmmh430ck foreign key (ATTR_ID) references UP_SS_DESC_LAY_ATTR
alter table UP_SS_DESC_OUTPUT_PROP add constraint FK_j4hmf1wlxtv514o6xqd9g1rkq foreign key (SS_DESC_ID) references UP_SS_DESC
alter table UP_SS_DESC_PARAM add constraint FK_9ckpxmguyriun7eq1l57pej79 foreign key (SS_DESC_ID) references UP_SS_DESC
alter table UP_SS_USER_PREF add constraint FK_ffsut1mr9t6sdtby7pm4brc3p foreign key (UP_SS_DESCRIPTOR_ID) references UP_SS_DESC
alter table UP_SS_USER_PREF add constraint UK_5a45fvux2roc7iy9ct3fre0ff unique (PROFILE_ID, UP_SS_DESCRIPTOR_ID, USER_ID)
alter table UP_SS_USER_PREF_LAY_ATTR add constraint FK_959w0xa3yub0fua4joy6evaog foreign key (SS_USER_PREF_ID) references UP_SS_USER_PREF
alter table UP_SS_USER_PREF_LAY_ATTR_VAL add constraint FK_esn4u5yf4875do543qas3u7b0 foreign key (UP_SS_USER_PREF_LAY_ATTR_ID) references UP_SS_USER_PREF_LAY_ATTR
alter table UP_SS_USER_PREF_OUTPUT_PROP add constraint FK_jvv6wrfl6y7g4p0so63i4hujq foreign key (SS_USER_PREF_ID) references UP_SS_USER_PREF
alter table UP_SS_USER_PREF_PARAM add constraint FK_532664n0k0c9ckj13e7gpya08 foreign key (SS_USER_PREF_ID) references UP_SS_USER_PREF
alter table UP_TENANT add constraint UK_ea8k2ourgxq1jigwh40k9fm1 unique (TENANT_NAME)
alter table UP_TENANT add constraint UK_gw8o79thm9v8h89f75xf9wm0y unique (TENANT_FNAME)
alter table UP_TENANT_ATTRIBUTES add constraint FK_egnmdn482aeixi5ww8ieo41jk foreign key (TENANT_ID) references UP_TENANT
alter table UP_VERSION add constraint UK_j3y9luccxamojumbsi0ksray8 unique (PRODUCT)
create index IDX_MESSAGE__CODE on UP_MESSAGE (CODE)
create index IDX_MESSAGE__LOCALE on UP_MESSAGE (LOCALE)
create index IDX_PORTLET_DEF__TITLE on UP_PORTLET_DEF (PORTLET_TITLE)
create index IDX_SS_USER_PREF_ID on UP_SS_USER_PREF_LAY_ATTR (SS_USER_PREF_ID)
create index IDX_UPP_PREFS_ID on UP_PORTLET_PREF (PORTLET_PREFS_ID)
create index IDX_UP_PORTLET_ENT__UP_USER on UP_PORTLET_ENT (USER_ID)
create index IDX_UP_PRTLT_CKS_EXP on UP_PORTLET_COOKIES (EXPIRES)
create index IDX_UP_PRTL_CK_EXP on UP_PORTAL_COOKIES (EXPIRES)
create table UP_DLM_EVALUATOR (EVALUATOR_TYPE varchar(31) not null, EVALUATOR_ID bigint not null, ENTITY_VERSION bigint, DESCRIPTION varchar(255), FRAGMENT_NAME varchar(255), OWNER_ID varchar(255), PRECEDENCE double precision, ATTRIBUTE_MODE int, ATTRIBUTE_NAME varchar(255), ATTRIBUTE_VALUE varchar(255), EVALUATOR_MODE int, GROUP_KEY varchar(255), GROUP_NAME varchar(255), PROFILE_FNAME varchar(255), PAREN_TYPE int, primary key (EVALUATOR_ID))
create table UP_DLM_EVALUATOR_PAREN (PAREN_EVAL_ID bigint not null, CHILD_EVAL_ID bigint not null, EVAL_INDEX int not null, primary key (PAREN_EVAL_ID, EVAL_INDEX))
create table UP_DLM_EVALUATOR_SEQ (next_val bigint)
create table UP_JGROUPS_AUTH (SERVICE_NAME varchar(100) not null, RANDOM_TOKEN varchar(4000), primary key (SERVICE_NAME))
create table UP_MESSAGE (ID bigint not null, CODE varchar(128) not null, LOCALE varchar(64) not null, VALUE varchar(1024) not null, primary key (ID))
create table UP_MESSAGE_SEQ (next_val bigint)
create table UP_MUTEX (MUTEX_ID bigint not null, ENTITY_VERSION bigint, LOCK_UPDATE datetime not null, LOCK_END datetime not null, LOCK_START datetime not null, LOCKED bit not null, MUTEX_NAME varchar(200) not null, PREV_SERVER_ID varchar(200), SERVER_ID varchar(200), primary key (MUTEX_ID))
create table UP_MUTEX_SEQ (next_val bigint)
create table UP_PAGS_GROUP (PAGS_GROUP_ID bigint not null, DESCRIPTION varchar(500), ENTITY_VERSION bigint, NAME varchar(500) not null, primary key (PAGS_GROUP_ID))
create table UP_PAGS_GROUP_MEMBERS (PAGS_GROUP_ID bigint not null, PAGS_GROUP_MEMBER_ID bigint not null, primary key (PAGS_GROUP_ID, PAGS_GROUP_MEMBER_ID))
create table UP_PAGS_GROUP_SEQ (next_val bigint)
create table UP_PAGS_TEST (PAGS_TEST_ID bigint not null, ATTRIBUTE_NAME varchar(500), ENTITY_VERSION bigint, TEST_VALUE varchar(500), TESTER_CLASS varchar(500), PAGS_TEST_GROUP_ID bigint not null, primary key (PAGS_TEST_ID))
create table UP_PAGS_TEST_GROUP (PAGS_TEST_GROUP_ID bigint not null, ENTITY_VERSION bigint, PAGS_GROUP_ID bigint not null, primary key (PAGS_TEST_GROUP_ID))
create table UP_PAGS_TEST_GROUP_SEQ (next_val bigint)
create table UP_PAGS_TEST_SEQ (next_val bigint)
create table UP_PERMISSION_ACTIVITY (ACTIVITY_ID bigint not null, ACTIVITY_DESCRIPTION varchar(255), ENTITY_VERSION bigint, ACTIVITY_FNAME varchar(128) not null, ACTIVITY_NAME varchar(128) not null, OWNER_TARGET_PROVIDER varchar(255) not null, OWNER_ID bigint, primary key (ACTIVITY_ID))
create table UP_PERMISSION_ACTIVITY_SEQ (next_val bigint)
create table UP_PERMISSION_OWNER (OWNER_ID bigint not null, OWNER_DESCRIPTION varchar(255), ENTITY_VERSION bigint, OWNER_FNAME varchar(128) not null, OWNER_NAME varchar(128) not null, primary key (OWNER_ID))
create table UP_PERMISSION_OWNER_SEQ (next_val bigint)
create table UP_PERSON_ATTR (id int not null, ENTITY_VERSION bigint, ATTR_NAME varchar(255) not null, USER_DIR_ID bigint not null, primary key (id))
create table UP_PERSON_ATTR_SEQ (next_val bigint)
create table UP_PERSON_ATTR_VALUES (ATTR_ID int not null, ATTR_VALUE varchar(255), VALUE_ORDER int not null, primary key (ATTR_ID, VALUE_ORDER))
create table UP_PERSON_DIR (USER_DIR_ID bigint not null, ENTITY_VERSION bigint, LST_PSWD_CGH_DT datetime, USER_NAME varchar(100) not null, ENCRPTD_PSWD varchar(256), primary key (USER_DIR_ID))
create table UP_PERSON_DIR_SEQ (next_val bigint)
create table UP_PORTAL_COOKIES (PORTAL_COOKIE_ID bigint not null, CREATED datetime not null, ENTITY_VERSION bigint, EXPIRES datetime not null, COOKIE_VALUE varchar(100) not null, primary key (PORTAL_COOKIE_ID))
create table UP_PORTAL_COOKIES_SEQ (next_val bigint)
create table UP_PORTLET_COOKIES (PORTLET_COOKIE_ID bigint not null, COOKIE_COMMENT varchar(1000), COOKIE_DOMAIN varchar(500), ENTITY_VERSION bigint, EXPIRES datetime not null, COOKIE_NAME varchar(500) not null, COOKIE_PATH varchar(1000), SECURE bit not null, COOKIE_VALUE varchar(1000) not null, VERSION int not null, portalCookie_PORTAL_COOKIE_ID bigint, PORTAL_COOKIE_ID bigint, primary key (PORTLET_COOKIE_ID))
create table UP_PORTLET_COOKIES_SEQ (next_val bigint)
create table UP_PORTLET_DEF (PORTLET_DEF_ID bigint not null, ACTION_TIMEOUT int, PORTLET_DESC varchar(255), ENTITY_VERSION bigint, EVENT_TIMEOUT int, PORTLET_FNAME varchar(255) not null, PORTLET_NAME varchar(128) not null, PORTLET_FRAMEWORK bit not null, PORTLET_APPLICATION_NAME varchar(255) not null, PORTLET_WEBAPP_NAME varchar(255), AVG_RATING double precision, RENDER_TIMEOUT int, RESOURCE_TIMEOUT int, PORTLET_TIMEOUT int not null, PORTLET_TITLE varchar(128) not null, AVG_RATING_USER_COUNT bigint, PORTLET_PREFS_ID bigint not null, PORTLET_TYPE_ID int not null, primary key (PORTLET_DEF_ID))
create table UP_PORTLET_DEF_MDATA (PORTLET_ID bigint not null, PORTLET_DESC varchar(255), PORTLET_NAME varchar(128), PORTLET_TITLE varchar(128), LOCALE varchar(64) not null, primary key (PORTLET_ID, LOCALE))
create table UP_PORTLET_DEF_PARAM (PARAM_ID bigint not null, PARAM_DESC varchar(255), PARAM_NAME varchar(255) not null, PARAM_VAL varchar(2000), PORTLET_DEF_ID bigint not null, primary key (PARAM_ID))
create table UP_PORTLET_DEF_PARAM_SEQ (next_val bigint)
create table UP_PORTLET_DEF_SEQ (next_val bigint)
create table UP_PORTLET_ENT (PORTLET_ENT_ID bigint not null, ENTITY_VERSION bigint, LAYOUT_NODE_ID varchar(255) not null, USER_ID int not null, PORTLET_DEF_ID bigint not null, PORTLET_PREFS_ID bigint not null, primary key (PORTLET_ENT_ID))
create table UP_PORTLET_ENT_SEQ (next_val bigint)
create table UP_PORTLET_ENT__STATES (PORTLET_ENT_ID bigint not null, WINDOW_STATE varchar(255), STYLESHEET_DESCRIPTOR_ID bigint not null, primary key (PORTLET_ENT_ID, STYLESHEET_DESCRIPTOR_ID))
create table UP_PORTLET_LIFECYCLE (ENTRY_ID bigint not null, ENTRY_DATE datetime, LIFECYCLE_STATE_ID int, USER_ID int, PORTLET_DEF_ID bigint not null, primary key (ENTRY_ID))
create table UP_PORTLET_LIFECYCLE_SEQ (next_val bigint)
create table UP_PORTLET_LIST (ID varchar(255) not null, CREATED_BY varchar(255) not null, CREATED_ON datetime not null, NAME varchar(255) not null, OWNER_USERNAME varchar(255) not null, UPDATED_BY varchar(255) not null, UPDATED_ON datetime not null, primary key (ID))
create table UP_PORTLET_LIST_ITEM (ID varchar(255) not null, ENTITY_ID varchar(255) not null, LIST_ORDER int not null, LIST_ID varchar(255) not null, primary key (ID))
create table UP_PORTLET_PREF (PORTLET_PREF_ID bigint not null, ENTITY_VERSION bigint, PREF_NAME varchar(MAX), NULL_VALUES bit not null, READ_ONLY bit not null, PORTLET_PREFS_ID bigint, PREF_ORDER int, primary key (PORTLET_PREF_ID))
create table UP_PORTLET_PREFS (PORTLET_PREFS_ID bigint not null, ENTITY_VERSION bigint, primary key (PORTLET_PREFS_ID))
create table UP_PORTLET_PREFS_SEQ (next_val bigint)
create table UP_PORTLET_PREF_SEQ (next_val bigint)
create table UP_PORTLET_PREF_VALUES (PORTLET_PREF_ID bigint not null, PREF_VALUE varchar(MAX), VALUE_ORDER int not null, primary key (PORTLET_PREF_ID, VALUE_ORDER))
create table UP_PORTLET_RATING (USER_NAME varchar(255) not null, RATING int, REVIEW varchar(160), PORTLET_ID bigint not null, primary key (PORTLET_ID, USER_NAME))
create table UP_PORTLET_TYPE (TYPE_ID int not null, TYPE_DEF_URI varchar(255) not null, TYPE_DESCR varchar(2000), ENTITY_VERSION bigint, TYPE_NAME varchar(70), primary key (TYPE_ID))
create table UP_PORTLET_TYPE_SEQ (next_val bigint)
create table UP_PROFILE_SELECTION (SELECTION_ID int not null, ENTITY_VERSION bigint, PROFILE_FNAME varchar(70) not null, USERNAME varchar(70) not null, primary key (SELECTION_ID))
create table UP_PROFILE_SELECTION_SEQ (next_val bigint)
create table UP_SS_DESC (SS_DESC_ID bigint not null, DESCRIPTION varchar(2000), ENTITY_VERSION bigint, SS_NAME varchar(100) not null, STYLESHEET varchar(2000) not null, URL_SYNTAX_HELPER_NAME varchar(100), primary key (SS_DESC_ID))
create table UP_SS_DESC_LAY_ATTR (SS_DESC_LAYOUT_ATTR_ID bigint not null, DEFAULT_VALUE varchar(500), DESCRIPTION varchar(2000), DATA_NAME varchar(100) not null, PERSISTENCE_SCOPE varchar(255) not null, ENTITY_VERSION bigint, SS_DESC_ID bigint not null, primary key (SS_DESC_LAYOUT_ATTR_ID))
create table UP_SS_DESC_LAY_ATTR_ELMS (ATTR_ID bigint not null, ELEMENT_NAME varchar(255))
create table UP_SS_DESC_LAY_ATTR_SEQ (next_val bigint)
create table UP_SS_DESC_OUTPUT_PROP (SS_DESC_LAYOUT_ATTR_ID bigint not null, DEFAULT_VALUE varchar(500), DESCRIPTION varchar(2000), DATA_NAME varchar(100) not null, PERSISTENCE_SCOPE varchar(255) not null, ENTITY_VERSION bigint, SS_DESC_ID bigint not null, primary key (SS_DESC_LAYOUT_ATTR_ID))
create table UP_SS_DESC_OUTPUT_PROP_SEQ (next_val bigint)
create table UP_SS_DESC_PARAM (SS_DESC_PARAM_ID bigint not null, DEFAULT_VALUE varchar(500), DESCRIPTION varchar(2000), DATA_NAME varchar(100) not null, PERSISTENCE_SCOPE varchar(255) not null, ENTITY_VERSION bigint, SS_DESC_ID bigint not null, primary key (SS_DESC_PARAM_ID))
create table UP_SS_DESC_PARAM_SEQ (next_val bigint)
create table UP_SS_DESC_SEQ (next_val bigint)
create table UP_SS_USER_PREF (SS_USER_PREF_ID bigint not null, ENTITY_VERSION bigint, PROFILE_ID int not null, USER_ID int not null, UP_SS_DESCRIPTOR_ID bigint not null, primary key (SS_USER_PREF_ID))
create table UP_SS_USER_PREF_LAY_ATTR (UP_SS_USER_PREF_LAY_ATTR_ID bigint not null, ENTITY_VERSION bigint, NODE_ID varchar(200) not null, SS_USER_PREF_ID bigint not null, primary key (UP_SS_USER_PREF_LAY_ATTR_ID))
create table UP_SS_USER_PREF_LAY_ATTR_SEQ (next_val bigint)
create table UP_SS_USER_PREF_LAY_ATTR_VAL (UP_SS_USER_PREF_LAY_ATTR_ID bigint not null, ATTR_VALUE varchar(2000) not null, ATTR_NAME varchar(500) not null, primary key (UP_SS_USER_PREF_LAY_ATTR_ID, ATTR_NAME))
create table UP_SS_USER_PREF_OUTPUT_PROP (SS_USER_PREF_ID bigint not null, PROP_VALUE varchar(2000) not null, PROP_NAME varchar(500) not null, primary key (SS_USER_PREF_ID, PROP_NAME))
create table UP_SS_USER_PREF_PARAM (SS_USER_PREF_ID bigint not null, PARAM_VALUE varchar(2000) not null, PARAM_NAME varchar(500) not null, primary key (SS_USER_PREF_ID, PARAM_NAME))
create table UP_SS_USER_PREF_SEQ (next_val bigint)
create table UP_TENANT (TENANT_ID bigint not null, TENANT_FNAME varchar(128) not null, TENANT_NAME varchar(255) not null, primary key (TENANT_ID))
create table UP_TENANT_ATTRIBUTES (TENANT_ID bigint not null, ATTR_VALUE varchar(2000) not null, ATTR_NAME varchar(500) not null, primary key (TENANT_ID, ATTR_NAME))
create table UP_TENANT_SEQ (next_val bigint)
create table UP_VERSION (VERSION_ID bigint not null, ENTITY_VERSION bigint, LOCAL_VER int, MAJOR_VER int not null, MINOR_VER int not null, PATCH_VER int not null, PRODUCT varchar(128) not null, primary key (VERSION_ID))
create table UP_VERSION_SEQ (next_val bigint)
insert into UP_DLM_EVALUATOR_SEQ values ( 1 )
insert into UP_MESSAGE_SEQ values ( 1 )
insert into UP_MUTEX_SEQ values ( 1 )
insert into UP_PAGS_GROUP_SEQ values ( 1 )
insert into UP_PAGS_TEST_GROUP_SEQ values ( 1 )
insert into UP_PAGS_TEST_SEQ values ( 1 )
insert into UP_PERMISSION_ACTIVITY_SEQ values ( 1 )
insert into UP_PERMISSION_OWNER_SEQ values ( 1 )
insert into UP_PERSON_ATTR_SEQ values ( 1 )
insert into UP_PERSON_DIR_SEQ values ( 1 )
insert into UP_PORTAL_COOKIES_SEQ values ( 1 )
insert into UP_PORTLET_COOKIES_SEQ values ( 1 )
insert into UP_PORTLET_DEF_PARAM_SEQ values ( 1 )
insert into UP_PORTLET_DEF_SEQ values ( 1 )
insert into UP_PORTLET_ENT_SEQ values ( 1 )
insert into UP_PORTLET_LIFECYCLE_SEQ values ( 1 )
insert into UP_PORTLET_PREFS_SEQ values ( 1 )
insert into UP_PORTLET_PREF_SEQ values ( 1 )
insert into UP_PORTLET_TYPE_SEQ values ( 1 )
insert into UP_PROFILE_SELECTION_SEQ values ( 1 )
insert into UP_SS_DESC_LAY_ATTR_SEQ values ( 1 )
insert into UP_SS_DESC_OUTPUT_PROP_SEQ values ( 1 )
insert into UP_SS_DESC_PARAM_SEQ values ( 1 )
insert into UP_SS_DESC_SEQ values ( 1 )
insert into UP_SS_USER_PREF_LAY_ATTR_SEQ values ( 1 )
insert into UP_SS_USER_PREF_SEQ values ( 1 )
insert into UP_TENANT_SEQ values ( 1 )
insert into UP_VERSION_SEQ values ( 1 )
-- PortalDb drop
alter table UP_DLM_EVALUATOR_PAREN drop constraint FK_3hbkemg0gydgc606l2io1x0gp
alter table UP_DLM_EVALUATOR_PAREN drop constraint FK_b3rertpqnnjrvd257fge2sj75
alter table UP_PAGS_GROUP_MEMBERS drop constraint FK_561gbtgfe5oi30yc2bspcoom
alter table UP_PAGS_GROUP_MEMBERS drop constraint FK_fsr7qaohryhcdv877sg60id6e
alter table UP_PAGS_TEST drop constraint FK_474tvkmldo7agadpwp0c7v3ko
alter table UP_PAGS_TEST_GROUP drop constraint FK_hygx3nc0f1dl8tgsmvte36ggo
alter table UP_PERMISSION_ACTIVITY drop constraint FK_16ncixqeffipy3vhia0bksvwy
alter table UP_PERSON_ATTR drop constraint FK_4s6n2w5mggpiif98ukmoyx5f8
alter table UP_PERSON_ATTR_VALUES drop constraint FK_qqexbdecsbkj4q5kiomuwn62d
alter table UP_PORTLET_COOKIES drop constraint FK_1eiw3v16g4l8uic967eefpupv
alter table UP_PORTLET_COOKIES drop constraint FK_5nbeljrfl8xrselnib4jlc8rm
alter table UP_PORTLET_DEF drop constraint FK_aw02n7y507j2o7a5unh3x6gmx
alter table UP_PORTLET_DEF drop constraint FK_sk16u3b6h5onabw0tkbfjb9t3
alter table UP_PORTLET_DEF_MDATA drop constraint FK_3mblqw8kx445e1wphecduvkal
alter table UP_PORTLET_DEF_PARAM drop constraint FK_8pr53vk0s7cj591svwm55mwuf
alter table UP_PORTLET_ENT drop constraint FK_9g5u5s6q6fyllrocmjknkgke1
alter table UP_PORTLET_ENT drop constraint FK_momja5hs0eruu7ke7naq542hn
alter table UP_PORTLET_ENT__STATES drop constraint FK_78ol5mt3uvfgxikpnhm4gc8hr
alter table UP_PORTLET_ENT__STATES drop constraint FK_fdl45hs325hvdevocoynuufgd
alter table UP_PORTLET_LIFECYCLE drop constraint FK_h2ih95p990rtn5nu74udal0ab
alter table UP_PORTLET_LIST_ITEM drop constraint FK_qsriu31b7uia45e0aj9odg368
alter table UP_PORTLET_PREF drop constraint FK_pgqclxrroe8ed8stf00pt2s93
alter table UP_PORTLET_PREF_VALUES drop constraint FK_tqk3058y17ttobwa3l8pc4fwd
alter table UP_PORTLET_RATING drop constraint FK_m8902ol9lpgf17b081uvfobyl
alter table UP_SS_DESC_LAY_ATTR drop constraint FK_meru2advdfypwhfddn5gnjnvy
alter table UP_SS_DESC_LAY_ATTR_ELMS drop constraint FK_2qvk7f3048lo65b5wmmh430ck
alter table UP_SS_DESC_OUTPUT_PROP drop constraint FK_j4hmf1wlxtv514o6xqd9g1rkq
alter table UP_SS_DESC_PARAM drop constraint FK_9ckpxmguyriun7eq1l57pej79
alter table UP_SS_USER_PREF drop constraint FK_ffsut1mr9t6sdtby7pm4brc3p
alter table UP_SS_USER_PREF_LAY_ATTR drop constraint FK_959w0xa3yub0fua4joy6evaog
alter table UP_SS_USER_PREF_LAY_ATTR_VAL drop constraint FK_esn4u5yf4875do543qas3u7b0
alter table UP_SS_USER_PREF_OUTPUT_PROP drop constraint FK_jvv6wrfl6y7g4p0so63i4hujq
alter table UP_SS_USER_PREF_PARAM drop constraint FK_532664n0k0c9ckj13e7gpya08
alter table UP_TENANT_ATTRIBUTES drop constraint FK_egnmdn482aeixi5ww8ieo41jk
drop table UP_DLM_EVALUATOR
drop table UP_DLM_EVALUATOR_PAREN
drop table UP_DLM_EVALUATOR_SEQ
drop table UP_JGROUPS_AUTH
drop table UP_MESSAGE
drop table UP_MESSAGE_SEQ
drop table UP_MUTEX
drop table UP_MUTEX_SEQ
drop table UP_PAGS_GROUP
drop table UP_PAGS_GROUP_MEMBERS
drop table UP_PAGS_GROUP_SEQ
drop table UP_PAGS_TEST
drop table UP_PAGS_TEST_GROUP
drop table UP_PAGS_TEST_GROUP_SEQ
drop table UP_PAGS_TEST_SEQ
drop table UP_PERMISSION_ACTIVITY
drop table UP_PERMISSION_ACTIVITY_SEQ
drop table UP_PERMISSION_OWNER
drop table UP_PERMISSION_OWNER_SEQ
drop table UP_PERSON_ATTR
drop table UP_PERSON_ATTR_SEQ
drop table UP_PERSON_ATTR_VALUES
drop table UP_PERSON_DIR
drop table UP_PERSON_DIR_SEQ
drop table UP_PORTAL_COOKIES
drop table UP_PORTAL_COOKIES_SEQ
drop table UP_PORTLET_COOKIES
drop table UP_PORTLET_COOKIES_SEQ
drop table UP_PORTLET_DEF
drop table UP_PORTLET_DEF_MDATA
drop table UP_PORTLET_DEF_PARAM
drop table UP_PORTLET_DEF_PARAM_SEQ
drop table UP_PORTLET_DEF_SEQ
drop table UP_PORTLET_ENT
drop table UP_PORTLET_ENT_SEQ
drop table UP_PORTLET_ENT__STATES
drop table UP_PORTLET_LIFECYCLE
drop table UP_PORTLET_LIFECYCLE_SEQ
drop table UP_PORTLET_LIST
drop table UP_PORTLET_LIST_ITEM
drop table UP_PORTLET_PREF
drop table UP_PORTLET_PREFS
drop table UP_PORTLET_PREFS_SEQ
drop table UP_PORTLET_PREF_SEQ
drop table UP_PORTLET_PREF_VALUES
drop table UP_PORTLET_RATING
drop table UP_PORTLET_TYPE
drop table UP_PORTLET_TYPE_SEQ
drop table UP_PROFILE_SELECTION
drop table UP_PROFILE_SELECTION_SEQ
drop table UP_SS_DESC
drop table UP_SS_DESC_LAY_ATTR
drop table UP_SS_DESC_LAY_ATTR_ELMS
drop table UP_SS_DESC_LAY_ATTR_SEQ
drop table UP_SS_DESC_OUTPUT_PROP
drop table UP_SS_DESC_OUTPUT_PROP_SEQ
drop table UP_SS_DESC_PARAM
drop table UP_SS_DESC_PARAM_SEQ
drop table UP_SS_DESC_SEQ
drop table UP_SS_USER_PREF
drop table UP_SS_USER_PREF_LAY_ATTR
drop table UP_SS_USER_PREF_LAY_ATTR_SEQ
drop table UP_SS_USER_PREF_LAY_ATTR_VAL
drop table UP_SS_USER_PREF_OUTPUT_PROP
drop table UP_SS_USER_PREF_PARAM
drop table UP_SS_USER_PREF_SEQ
drop table UP_TENANT
drop table UP_TENANT_ATTRIBUTES
drop table UP_TENANT_SEQ
drop table UP_VERSION
drop table UP_VERSION_SEQ
-- RawEventsDb create
create index IDX_UP_ANALYTICS_EVENTS_SERVER_ID on UP_ANALYTICS_EVENTS (SERVER_ID)
create index IDX_UP_ANALYTICS_EVENTS_SESSION_ID on UP_ANALYTICS_EVENTS (SESSION_ID)
create index IDX_UP_ANALYTICS_EVENTS_TIMESTAMP on UP_ANALYTICS_EVENTS (TIMESTAMP)
create index IDX_UP_ANALYTICS_EVENTS_USER_NAME on UP_ANALYTICS_EVENTS (USER_NAME)
create index IDX_UP_RAW_EVENTS_AGGREGATED on UP_RAW_EVENTS (AGGREGATED)
create index IDX_UP_RAW_EVENTS_ERR_AGGR on UP_RAW_EVENTS (ERROR_AGGR)
create index IDX_UP_RAW_EVENTS_SERVER_ID on UP_RAW_EVENTS (SERVER_ID)
create index IDX_UP_RAW_EVENTS_SESSION_ID on UP_RAW_EVENTS (SESSION_ID)
create index IDX_UP_RAW_EVENTS_TIMESTAMP on UP_RAW_EVENTS (TIMESTAMP)
create index IDX_UP_RAW_EVENTS_USER_NAME on UP_RAW_EVENTS (USER_NAME)
create table UP_ANALYTICS_EVENTS (EVENT_ID bigint not null, EVENT_DATA varchar(MAX) not null, SESSION_ID varchar(500) not null, EVENT_TYPE varchar(200) not null, SERVER_ID varchar(200) not null, TIMESTAMP datetime not null, USER_NAME varchar(100) not null, primary key (EVENT_ID))
create table UP_ANALYTICS_EVENTS_SEQ (next_val bigint)
create table UP_RAW_EVENTS (EVENT_ID bigint not null, AGGREGATED bit, ERROR_AGGR bit, EVENT_DATA varchar(MAX) not null, SESSION_ID varchar(500) not null, EVENT_TYPE varchar(200) not null, SERVER_ID varchar(200) not null, TIMESTAMP datetime not null, USER_NAME varchar(100) not null, primary key (EVENT_ID))
create table UP_RAW_EVENTS_SEQ (next_val bigint)
insert into UP_ANALYTICS_EVENTS_SEQ values ( 1 )
insert into UP_RAW_EVENTS_SEQ values ( 1 )
-- RawEventsDb drop
drop table UP_ANALYTICS_EVENTS
drop table UP_ANALYTICS_EVENTS_SEQ
drop table UP_RAW_EVENTS
drop table UP_RAW_EVENTS_SEQ
-- AggrEventsDb create
alter table UP_ACADEMIC_TERM_DETAIL add constraint UK_h3gl6cihs58jmkw0p8rxctq6e unique (TERM_END, TERM_START)
alter table UP_AGGR_GROUP_MAPPING add constraint UK_nrmtcrt3gptt26te8fpbxbb4e unique (GROUP_NAME, GROUP_SERVICE)
alter table UP_AGGR_PORTLET_MAPPING add constraint UK_q4ftp6nhgswiqanlg780qiwcu unique (PORTLET_FNAME)
alter table UP_AGGR_TAB_MAPPING add constraint UK_12yxp003diq8eq805d5m2csj6 unique (FRAGMENT_NAME, TAB_NAME)
alter table UP_CONCURRENT_USER_AGGR add constraint FK_1crh0u5hdegnhoauxieag3a4r foreign key (TIME_DIMENSION_ID) references UP_TIME_DIMENSION
alter table UP_CONCURRENT_USER_AGGR add constraint FK_2nygg0lvedcjb61ku9091b6dn foreign key (DATE_DIMENSION_ID) references UP_DATE_DIMENSION
alter table UP_CONCURRENT_USER_AGGR add constraint FK_ah140ktotfckypsu0bikxxdms foreign key (AGGR_GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_CONCURRENT_USER_AGGR add constraint FK_fevflomiojm7s6aka5ar6md9b foreign key (UNIQUE_STRINGS_ID) references UP_UNIQUE_STR
alter table UP_CONCURRENT_USER_AGGR add constraint UK_m45t21vwp58e3wsj4gkweeorn unique (AGGR_GROUP_ID, DATE_DIMENSION_ID, AGGR_INTERVAL, TIME_DIMENSION_ID)
alter table UP_DATE_DIMENSION add constraint UK_5kwskd18xjj0wlptbm8eo1x0c unique (DD_DATE)
alter table UP_EVENT_AGGR_CONF_GROUPS add constraint UK_l5try4bca55a1p0wg6x6b44qh unique (AGGREGATOR_TYPE)
alter table UP_EVENT_AGGR_CONF_GROUPS_EXC add constraint FK_74rcg2txnpblpo44d3o06d7rf foreign key (UP_EVENT_AGGR_CONF_GROUPS_ID) references UP_EVENT_AGGR_CONF_GROUPS
alter table UP_EVENT_AGGR_CONF_GROUPS_EXC add constraint FK_9jw0rlw4hvpybrd8usmwua0jm foreign key (GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_EVENT_AGGR_CONF_GROUPS_EXC add constraint UK_9jw0rlw4hvpybrd8usmwua0jm unique (GROUP_ID)
alter table UP_EVENT_AGGR_CONF_GROUPS_INC add constraint FK_943ene82efvoquc5lb9kj4s75 foreign key (GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_EVENT_AGGR_CONF_GROUPS_INC add constraint FK_iao8dyvaqe3eofd3tsov4ajvj foreign key (UP_EVENT_AGGR_CONF_GROUPS_ID) references UP_EVENT_AGGR_CONF_GROUPS
alter table UP_EVENT_AGGR_CONF_GROUPS_INC add constraint UK_943ene82efvoquc5lb9kj4s75 unique (GROUP_ID)
alter table UP_EVENT_AGGR_CONF_INTRVL add constraint UK_3u4sinvms4movbo7d84dy5kdh unique (AGGREGATOR_TYPE)
alter table UP_EVENT_AGGR_CONF_INTRVL_EXC add constraint FK_olivv7buxiyxirms24kwnltdv foreign key (UP_EVENT_AGGR_CONF_INTRVL_ID) references UP_EVENT_AGGR_CONF_INTRVL
alter table UP_EVENT_AGGR_CONF_INTRVL_INC add constraint FK_5bn3fmi5gtm9sodb7tra6ifj3 foreign key (UP_EVENT_AGGR_CONF_INTRVL_ID) references UP_EVENT_AGGR_CONF_INTRVL
alter table UP_EVENT_AGGR_STATUS add constraint UK_mwta3uf64ob3h78rpg6gslo unique (PROCESSING_TYPE)
alter table UP_EVENT_SESSION add constraint UK_giqfk378nexxed95q5ogmb0yj unique (SESSION_ID)
alter table UP_EVENT_SESSION_GROUPS add constraint FK_59xvrgdwm8ti0jfk8aboi3bxq foreign key (UP_EVENT_SESSION_ID) references UP_EVENT_SESSION
alter table UP_EVENT_SESSION_GROUPS add constraint FK_f955axnw72t6o8j9pg7ar126s foreign key (GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_LOGIN_EVENT_AGGR add constraint FK_a0ytd5or6pogsgpiw2dwrekar foreign key (UNIQUE_STRINGS_ID) references UP_UNIQUE_STR
alter table UP_LOGIN_EVENT_AGGR add constraint FK_b3e3bhqcgbavdw2ooy5lc32du foreign key (TIME_DIMENSION_ID) references UP_TIME_DIMENSION
alter table UP_LOGIN_EVENT_AGGR add constraint FK_i0jl7o218lqxqaqt0ihyycayt foreign key (DATE_DIMENSION_ID) references UP_DATE_DIMENSION
alter table UP_LOGIN_EVENT_AGGR add constraint FK_j242b9y972r8yjm9lhmw4ukfk foreign key (AGGR_GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_LOGIN_EVENT_AGGR add constraint UK_8ib84bs8q3a7knrcvw8cmkljw unique (AGGR_GROUP_ID, DATE_DIMENSION_ID, AGGR_INTERVAL, TIME_DIMENSION_ID)
alter table UP_PORTLET_EXEC_AGGR add constraint FK_5dm3bkqehgn813y9m789w9lsj foreign key (AGGR_GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_PORTLET_EXEC_AGGR add constraint FK_88xcdt0n49vgpjhq45wsb615h foreign key (AGGR_PORTLET_ID) references UP_AGGR_PORTLET_MAPPING
alter table UP_PORTLET_EXEC_AGGR add constraint FK_h2a84m5e2ofpr3ma9dbksojus foreign key (DATE_DIMENSION_ID) references UP_DATE_DIMENSION
alter table UP_PORTLET_EXEC_AGGR add constraint FK_rs4dl1aufde7lei12ssq1ama9 foreign key (TIME_DIMENSION_ID) references UP_TIME_DIMENSION
alter table UP_PORTLET_EXEC_AGGR add constraint UK_990hgx1f1hsp9ss5wcdvskyec unique (AGGR_GROUP_ID, DATE_DIMENSION_ID, AGGR_INTERVAL, TIME_DIMENSION_ID, AGGR_PORTLET_ID, EXECUTION_TYPE)
alter table UP_PORTLET_LAYOUT_AGGR add constraint FK_2ymw67vmy3w726tqmubl5vy7x foreign key (TIME_DIMENSION_ID) references UP_TIME_DIMENSION
alter table UP_PORTLET_LAYOUT_AGGR add constraint FK_ccxeenr6yku33fgaj2sdr26vm foreign key (AGGR_PORTLET_ID) references UP_AGGR_PORTLET_MAPPING
alter table UP_PORTLET_LAYOUT_AGGR add constraint FK_p4p3gpngs4fr6nxm9ot7kd4n foreign key (DATE_DIMENSION_ID) references UP_DATE_DIMENSION
alter table UP_PORTLET_LAYOUT_AGGR add constraint FK_rnqay4ktmvb4tir3hbuxpdd3m foreign key (AGGR_GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_PORTLET_LAYOUT_AGGR add constraint UK_2hs7cvsd60i1x4cl4myq30f4f unique (AGGR_GROUP_ID, DATE_DIMENSION_ID, AGGR_INTERVAL, TIME_DIMENSION_ID, AGGR_PORTLET_ID)
alter table UP_QUARTER_DETAIL add constraint UK_a6f6djxsml79jyhlx2tyfy8gx unique (QUARTER_END, QUARTER_NUMBER, QUARTER_START)
alter table UP_SEARCH_REQ_AGGR add constraint FK_5qs16vldyls8s8l4wddduvwas foreign key (TIME_DIMENSION_ID) references UP_TIME_DIMENSION
alter table UP_SEARCH_REQ_AGGR add constraint FK_fdsmb9vgbgr38u1y8nxp9d9pn foreign key (AGGR_GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_SEARCH_REQ_AGGR add constraint FK_ki95eyb5kx2way36sksiewt8m foreign key (DATE_DIMENSION_ID) references UP_DATE_DIMENSION
alter table UP_SEARCH_REQ_AGGR add constraint UK_tbujmrks9rhe0ug5ddio6axaq unique (AGGR_GROUP_ID, DATE_DIMENSION_ID, AGGR_INTERVAL, TIME_DIMENSION_ID, SEARCH_TERM)
alter table UP_TAB_RENDER_AGGR add constraint FK_8enpaf5sqybuiyyhcy5o1p2so foreign key (AGGR_TAB_ID) references UP_AGGR_TAB_MAPPING
alter table UP_TAB_RENDER_AGGR add constraint FK_9vmi8jjue0tvdv18f8gxp061h foreign key (TIME_DIMENSION_ID) references UP_TIME_DIMENSION
alter table UP_TAB_RENDER_AGGR add constraint FK_fg956a15thl835b0q60w24w3q foreign key (DATE_DIMENSION_ID) references UP_DATE_DIMENSION
alter table UP_TAB_RENDER_AGGR add constraint FK_qi52trj4gl7a8p53yyl4scsy9 foreign key (AGGR_GROUP_ID) references UP_AGGR_GROUP_MAPPING
alter table UP_TAB_RENDER_AGGR add constraint UK_r3uvcgag0blkx26bn4i9mmatu unique (AGGR_GROUP_ID, DATE_DIMENSION_ID, AGGR_INTERVAL, TIME_DIMENSION_ID, AGGR_TAB_ID)
alter table UP_TIME_DIMENSION add constraint UK_5ug8chvtlslmrbriy407orck unique (TD_TIME)
alter table UP_UNIQUE_STR_SEGMENT add constraint FK_e3lqm10qhd0u7mu10assdfxx8 foreign key (UNIQUE_STR_ID) references UP_UNIQUE_STR
alter table UP_UNIQUE_STR_SEGMENT__UIDS add constraint FK_et6ttoxrppodvhd4gb3mm1ypt foreign key (UNIQUE_STR_SEGMENT_ID) references UP_UNIQUE_STR_SEGMENT
create index IDX_UP_CONC_USER_AGGR_DTI on UP_CONCURRENT_USER_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL)
create index IDX_UP_CONC_USER_GRP on UP_CONCURRENT_USER_AGGR (AGGR_GROUP_ID)
create index IDX_UP_CONC_USER_INTRVL on UP_CONCURRENT_USER_AGGR (AGGR_INTERVAL)
create index IDX_UP_DD_DAY on UP_DATE_DIMENSION (DD_DAY)
create index IDX_UP_DD_MONTH on UP_DATE_DIMENSION (DD_MONTH)
create index IDX_UP_DD_QUARTER on UP_DATE_DIMENSION (DD_QUARTER)
create index IDX_UP_DD_TERM on UP_DATE_DIMENSION (DD_TERM)
create index IDX_UP_DD_WEEK on UP_DATE_DIMENSION (DD_WEEK)
create index IDX_UP_DD_YEAR on UP_DATE_DIMENSION (DD_YEAR)
create index IDX_UP_EVENT_SESSION_DATE on UP_EVENT_SESSION (LAST_ACCESSED)
create index IDX_UP_LOGIN_EVENT_AGGR_DTI on UP_LOGIN_EVENT_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL)
create index IDX_UP_LOGIN_EVENT_GRP on UP_LOGIN_EVENT_AGGR (AGGR_GROUP_ID)
create index IDX_UP_LOGIN_EVENT_INTRVL on UP_LOGIN_EVENT_AGGR (AGGR_INTERVAL)
create index IDX_UP_PLT_EXEC_AGGR_DTI on UP_PORTLET_EXEC_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL)
create index IDX_UP_PLT_EXEC_AGGR_DTIC on UP_PORTLET_EXEC_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL, STATS_COMPLETE)
create index IDX_UP_PLT_EXEC_GRP on UP_PORTLET_EXEC_AGGR (AGGR_GROUP_ID)
create index IDX_UP_PLT_EXEC_INTRVL on UP_PORTLET_EXEC_AGGR (AGGR_INTERVAL)
create index IDX_UP_PORTLET_LAYOUT_AGGR_DTI on UP_PORTLET_LAYOUT_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL)
create index IDX_UP_PORTLET_LAYOUT_GRP on UP_PORTLET_LAYOUT_AGGR (AGGR_GROUP_ID)
create index IDX_UP_PORTLET_LAYOUT_INTRVL on UP_PORTLET_LAYOUT_AGGR (AGGR_INTERVAL)
create index IDX_UP_SEARCH_REQ_AGGR_DTI on UP_SEARCH_REQ_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL)
create index IDX_UP_SEARCH_REQ_GRP on UP_SEARCH_REQ_AGGR (AGGR_GROUP_ID)
create index IDX_UP_SEARCH_REQ_INTRVL on UP_SEARCH_REQ_AGGR (AGGR_INTERVAL)
create index IDX_UP_SEARCH_REQ_TRM on UP_SEARCH_REQ_AGGR (SEARCH_TERM)
create index IDX_UP_TAB_REND_AGGR_DTI on UP_TAB_RENDER_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL)
create index IDX_UP_TAB_REND_AGGR_DTIC on UP_TAB_RENDER_AGGR (DATE_DIMENSION_ID, TIME_DIMENSION_ID, AGGR_INTERVAL, STATS_COMPLETE)
create index IDX_UP_TAB_REND_GRP on UP_TAB_RENDER_AGGR (AGGR_GROUP_ID)
create index IDX_UP_TAB_REND_INTRVL on UP_TAB_RENDER_AGGR (AGGR_INTERVAL)
create index IDX_UP_TD_FIVE_MIN_INCR on UP_TIME_DIMENSION (TD_FIVE_MINUTE_INCREMENT)
create index IDX_UP_TD_HOUR on UP_TIME_DIMENSION (TD_HOUR)
create index IDX_UP_TD_MINUTE on UP_TIME_DIMENSION (TD_MINUTE)
create table UP_ACADEMIC_TERM_DETAIL (TERM_ID bigint not null, TERM_END datetime not null, TERM_START datetime not null, TERM_NAME varchar(255) not null, primary key (TERM_ID))
create table UP_ACADEMIC_TERM_DETAIL_SEQ (next_val bigint)
create table UP_AGGR_GROUP_MAPPING (ID bigint not null, GROUP_NAME varchar(200) not null, GROUP_SERVICE varchar(200) not null, primary key (ID))
create table UP_AGGR_GROUP_MAPPING_SEQ (next_val bigint)
create table UP_AGGR_PORTLET_MAPPING (ID bigint not null, PORTLET_FNAME varchar(255) not null, PORTLET_NAME varchar(128) not null, primary key (ID))
create table UP_AGGR_PORTLET_MAPPING_SEQ (next_val bigint)
create table UP_AGGR_TAB_MAPPING (ID bigint not null, FRAGMENT_NAME varchar(200) not null, TAB_NAME varchar(200) not null, primary key (ID))
create table UP_AGGR_TAB_MAPPING_SEQ (next_val bigint)
create table UP_CONCURRENT_USER_AGGR (ID bigint not null, DURATION int not null, AGGR_INTERVAL varchar(255) not null, CONCURRENT_USERS int not null, AGGR_GROUP_ID bigint not null, DATE_DIMENSION_ID bigint not null, TIME_DIMENSION_ID bigint not null, UNIQUE_STRINGS_ID bigint, primary key (ID))
create table UP_CONCURRENT_USER_AGGR_SEQ (next_val bigint)
create table UP_DATE_DIMENSION (DATE_ID bigint not null, DD_DATE datetime not null, DD_DAY int not null, DD_MONTH int not null, DD_QUARTER int not null, DD_TERM varchar(200), DD_WEEK int not null, DD_YEAR int not null, primary key (DATE_ID))
create table UP_DATE_DIMENSION_SEQ (next_val bigint)
create table UP_EVENT_AGGR_CONF_GROUPS (ID bigint not null, AGGREGATOR_TYPE varchar(255) not null, ENTITY_VERSION bigint, primary key (ID))
create table UP_EVENT_AGGR_CONF_GROUPS_EXC (UP_EVENT_AGGR_CONF_GROUPS_ID bigint not null, GROUP_ID bigint not null, primary key (UP_EVENT_AGGR_CONF_GROUPS_ID, GROUP_ID))
create table UP_EVENT_AGGR_CONF_GROUPS_INC (UP_EVENT_AGGR_CONF_GROUPS_ID bigint not null, GROUP_ID bigint not null, primary key (UP_EVENT_AGGR_CONF_GROUPS_ID, GROUP_ID))
create table UP_EVENT_AGGR_CONF_GROUPS_SEQ (next_val bigint)
create table UP_EVENT_AGGR_CONF_INTRVL (ID bigint not null, AGGREGATOR_TYPE varchar(255) not null, ENTITY_VERSION bigint, primary key (ID))
create table UP_EVENT_AGGR_CONF_INTRVL_EXC (UP_EVENT_AGGR_CONF_INTRVL_ID bigint not null, AGGR_INTERVAL varchar(255))
create table UP_EVENT_AGGR_CONF_INTRVL_INC (UP_EVENT_AGGR_CONF_INTRVL_ID bigint not null, AGGR_INTERVAL varchar(255))
create table UP_EVENT_AGGR_CONF_INTRVL_SEQ (next_val bigint)
create table UP_EVENT_AGGR_STATUS (ID bigint not null, ENTITY_VERSION bigint, LAST_END datetime, LAST_EVENT_DATE datetime, LAST_START datetime, PROCESSING_TYPE varchar(255) not null, SERVER_NAME varchar(200), primary key (ID))
create table UP_EVENT_AGGR_STATUS_SEQ (next_val bigint)
create table UP_EVENT_SESSION (ID bigint not null, SESSION_ID varchar(500) not null, LAST_ACCESSED datetime not null, primary key (ID))
create table UP_EVENT_SESSION_GROUPS (UP_EVENT_SESSION_ID bigint not null, GROUP_ID bigint not null, primary key (UP_EVENT_SESSION_ID, GROUP_ID))
create table UP_EVENT_SESSION_SEQ (next_val bigint)
create table UP_LOGIN_EVENT_AGGR (ID bigint not null, DURATION int not null, AGGR_INTERVAL varchar(255) not null, LOGIN_COUNT int not null, UNIQUE_LOGIN_COUNT int not null, AGGR_GROUP_ID bigint not null, DATE_DIMENSION_ID bigint not null, TIME_DIMENSION_ID bigint not null, UNIQUE_STRINGS_ID bigint, primary key (ID))
create table UP_LOGIN_EVENT_AGGR_SEQ (next_val bigint)
create table UP_PORTLET_EXEC_AGGR (ID bigint not null, DURATION int not null, AGGR_INTERVAL varchar(255) not null, STATS_COMPLETE bit not null, TIME_COUNT int not null, GEOMETRIC_MEAN_TIME double precision not null, MAX_TIME double precision not null, MEAN_TIME double precision not null, MIN_TIME double precision not null, POPULATION_VARIANCE_TIME double precision not null, SECOND_MOMENT_TIME double precision not null, STD_DEVIATION_TIME double precision not null, CM_MAX_N bigint, CM_MAX_VALUE double precision, CM_MIN_N bigint, CM_MIN_VALUE double precision, CM_2MMNT_M2 double precision, CM_1MMNT_DEV double precision, CM_1MMNT_M1 double precision, CM_1MMNT_N bigint, CM_1MMNT_NDEV double precision, CM_SUM_N bigint, CM_SUM_VALUE double precision, CM_SUMOLOGS_N int, CM_SUMOLOGS_VALUE double precision, CM_SUMOSQRS_N bigint, CM_SUMOSQRS_VALUE double precision, SUM_TIME double precision not null, SUM_OF_LOGS_TIME double precision not null, SUMSQ_TIME double precision not null, VARIANCE_TIME double precision not null, EXECUTION_TYPE varchar(50) not null, AGGR_GROUP_ID bigint not null, DATE_DIMENSION_ID bigint not null, TIME_DIMENSION_ID bigint not null, AGGR_PORTLET_ID bigint not null, primary key (ID))
create table UP_PORTLET_EXEC_AGGR_SEQ (next_val bigint)
create table UP_PORTLET_LAYOUT_AGGR (ID bigint not null, DURATION int not null, AGGR_INTERVAL varchar(255) not null, ADD_COUNT int not null, STATS_COMPLETE bit not null, DEL_COUNT int not null, MOVE_COUNT int not null, AGGR_GROUP_ID bigint not null, DATE_DIMENSION_ID bigint not null, TIME_DIMENSION_ID bigint not null, AGGR_PORTLET_ID bigint not null, primary key (ID))
create table UP_PORTLET_LAYOUT_AGGR_SEQ (next_val bigint)
create table UP_QUARTER_DETAIL (QUARTER_ID bigint not null, QUARTER_END varchar(255) not null, QUARTER_NUMBER int not null, QUARTER_START varchar(255) not null, primary key (QUARTER_ID))
create table UP_QUARTER_DETAIL_SEQ (next_val bigint)
create table UP_SEARCH_REQ_AGGR (ID bigint not null, DURATION int not null, AGGR_INTERVAL varchar(255) not null, STATS_COMPLETE bit not null, SEARCH_COUNT int not null, SEARCH_TERM varchar(255) not null, AGGR_GROUP_ID bigint not null, DATE_DIMENSION_ID bigint not null, TIME_DIMENSION_ID bigint not null, primary key (ID))
create table UP_SEARCH_REQ_AGGR_SEQ (next_val bigint)
create table UP_TAB_RENDER_AGGR (ID bigint not null, DURATION int not null, AGGR_INTERVAL varchar(255) not null, STATS_COMPLETE bit not null, TIME_COUNT int not null, GEOMETRIC_MEAN_TIME double precision not null, MAX_TIME double precision not null, MEAN_TIME double precision not null, MIN_TIME double precision not null, POPULATION_VARIANCE_TIME double precision not null, SECOND_MOMENT_TIME double precision not null, STD_DEVIATION_TIME double precision not null, CM_MAX_N bigint, CM_MAX_VALUE double precision, CM_MIN_N bigint, CM_MIN_VALUE double precision, CM_2MMNT_M2 double precision, CM_1MMNT_DEV double precision, CM_1MMNT_M1 double precision, CM_1MMNT_N bigint, CM_1MMNT_NDEV double precision, CM_SUM_N bigint, CM_SUM_VALUE double precision, CM_SUMOLOGS_N int, CM_SUMOLOGS_VALUE double precision, CM_SUMOSQRS_N bigint, CM_SUMOSQRS_VALUE double precision, SUM_TIME double precision not null, SUM_OF_LOGS_TIME double precision not null, SUMSQ_TIME double precision not null, VARIANCE_TIME double precision not null, AGGR_GROUP_ID bigint not null, DATE_DIMENSION_ID bigint not null, TIME_DIMENSION_ID bigint not null, AGGR_TAB_ID bigint not null, primary key (ID))
create table UP_TAB_RENDER_AGGR_SEQ (next_val bigint)
create table UP_TIME_DIMENSION (TIME_ID bigint not null, TD_FIVE_MINUTE_INCREMENT int not null, TD_HOUR int not null, TD_MINUTE int not null, TD_TIME datetime not null, primary key (TIME_ID))
create table UP_TIME_DIMENSION_SEQ (next_val bigint)
create table UP_UNIQUE_STR (UNIQUE_STR_ID bigint not null, primary key (UNIQUE_STR_ID))
create table UP_UNIQUE_STR_SEGMENT (UNIQUE_STR_SEGMENT_ID bigint not null, UNIQUE_STR_ID bigint not null, primary key (UNIQUE_STR_SEGMENT_ID))
create table UP_UNIQUE_STR_SEGMENT_SEQ (next_val bigint)
create table UP_UNIQUE_STR_SEGMENT__UIDS (UNIQUE_STR_SEGMENT_ID bigint not null, UNIQUE_STR varchar(255) not null, primary key (UNIQUE_STR_SEGMENT_ID, UNIQUE_STR))
create table UP_UNIQUE_STR_SEQ (next_val bigint)
insert into UP_ACADEMIC_TERM_DETAIL_SEQ values ( 1 )
insert into UP_AGGR_GROUP_MAPPING_SEQ values ( 1 )
insert into UP_AGGR_PORTLET_MAPPING_SEQ values ( 1 )
insert into UP_AGGR_TAB_MAPPING_SEQ values ( 1 )
insert into UP_CONCURRENT_USER_AGGR_SEQ values ( 1 )
insert into UP_DATE_DIMENSION_SEQ values ( 1 )
insert into UP_EVENT_AGGR_CONF_GROUPS_SEQ values ( 1 )
insert into UP_EVENT_AGGR_CONF_INTRVL_SEQ values ( 1 )
insert into UP_EVENT_AGGR_STATUS_SEQ values ( 1 )
insert into UP_EVENT_SESSION_SEQ values ( 1 )
insert into UP_LOGIN_EVENT_AGGR_SEQ values ( 1 )
insert into UP_PORTLET_EXEC_AGGR_SEQ values ( 1 )
insert into UP_PORTLET_LAYOUT_AGGR_SEQ values ( 1 )
insert into UP_QUARTER_DETAIL_SEQ values ( 1 )
insert into UP_SEARCH_REQ_AGGR_SEQ values ( 1 )
insert into UP_TAB_RENDER_AGGR_SEQ values ( 1 )
insert into UP_TIME_DIMENSION_SEQ values ( 1 )
insert into UP_UNIQUE_STR_SEGMENT_SEQ values ( 1 )
insert into UP_UNIQUE_STR_SEQ values ( 1 )
-- AggrEventsDb drop
alter table UP_CONCURRENT_USER_AGGR drop constraint FK_1crh0u5hdegnhoauxieag3a4r
alter table UP_CONCURRENT_USER_AGGR drop constraint FK_2nygg0lvedcjb61ku9091b6dn
alter table UP_CONCURRENT_USER_AGGR drop constraint FK_ah140ktotfckypsu0bikxxdms
alter table UP_CONCURRENT_USER_AGGR drop constraint FK_fevflomiojm7s6aka5ar6md9b
alter table UP_EVENT_AGGR_CONF_GROUPS_EXC drop constraint FK_74rcg2txnpblpo44d3o06d7rf
alter table UP_EVENT_AGGR_CONF_GROUPS_EXC drop constraint FK_9jw0rlw4hvpybrd8usmwua0jm
alter table UP_EVENT_AGGR_CONF_GROUPS_INC drop constraint FK_943ene82efvoquc5lb9kj4s75
alter table UP_EVENT_AGGR_CONF_GROUPS_INC drop constraint FK_iao8dyvaqe3eofd3tsov4ajvj
alter table UP_EVENT_AGGR_CONF_INTRVL_EXC drop constraint FK_olivv7buxiyxirms24kwnltdv
alter table UP_EVENT_AGGR_CONF_INTRVL_INC drop constraint FK_5bn3fmi5gtm9sodb7tra6ifj3
alter table UP_EVENT_SESSION_GROUPS drop constraint FK_59xvrgdwm8ti0jfk8aboi3bxq
alter table UP_EVENT_SESSION_GROUPS drop constraint FK_f955axnw72t6o8j9pg7ar126s
alter table UP_LOGIN_EVENT_AGGR drop constraint FK_a0ytd5or6pogsgpiw2dwrekar
alter table UP_LOGIN_EVENT_AGGR drop constraint FK_b3e3bhqcgbavdw2ooy5lc32du
alter table UP_LOGIN_EVENT_AGGR drop constraint FK_i0jl7o218lqxqaqt0ihyycayt
alter table UP_LOGIN_EVENT_AGGR drop constraint FK_j242b9y972r8yjm9lhmw4ukfk
alter table UP_PORTLET_EXEC_AGGR drop constraint FK_5dm3bkqehgn813y9m789w9lsj
alter table UP_PORTLET_EXEC_AGGR drop constraint FK_88xcdt0n49vgpjhq45wsb615h
alter table UP_PORTLET_EXEC_AGGR drop constraint FK_h2a84m5e2ofpr3ma9dbksojus
alter table UP_PORTLET_EXEC_AGGR drop constraint FK_rs4dl1aufde7lei12ssq1ama9
alter table UP_PORTLET_LAYOUT_AGGR drop constraint FK_2ymw67vmy3w726tqmubl5vy7x
alter table UP_PORTLET_LAYOUT_AGGR drop constraint FK_ccxeenr6yku33fgaj2sdr26vm
alter table UP_PORTLET_LAYOUT_AGGR drop constraint FK_p4p3gpngs4fr6nxm9ot7kd4n
alter table UP_PORTLET_LAYOUT_AGGR drop constraint FK_rnqay4ktmvb4tir3hbuxpdd3m
alter table UP_SEARCH_REQ_AGGR drop constraint FK_5qs16vldyls8s8l4wddduvwas
alter table UP_SEARCH_REQ_AGGR drop constraint FK_fdsmb9vgbgr38u1y8nxp9d9pn
alter table UP_SEARCH_REQ_AGGR drop constraint FK_ki95eyb5kx2way36sksiewt8m
alter table UP_TAB_RENDER_AGGR drop constraint FK_8enpaf5sqybuiyyhcy5o1p2so
alter table UP_TAB_RENDER_AGGR drop constraint FK_9vmi8jjue0tvdv18f8gxp061h
alter table UP_TAB_RENDER_AGGR drop constraint FK_fg956a15thl835b0q60w24w3q
alter table UP_TAB_RENDER_AGGR drop constraint FK_qi52trj4gl7a8p53yyl4scsy9
alter table UP_UNIQUE_STR_SEGMENT drop constraint FK_e3lqm10qhd0u7mu10assdfxx8
alter table UP_UNIQUE_STR_SEGMENT__UIDS drop constraint FK_et6ttoxrppodvhd4gb3mm1ypt
drop table UP_ACADEMIC_TERM_DETAIL
drop table UP_ACADEMIC_TERM_DETAIL_SEQ
drop table UP_AGGR_GROUP_MAPPING
drop table UP_AGGR_GROUP_MAPPING_SEQ
drop table UP_AGGR_PORTLET_MAPPING
drop table UP_AGGR_PORTLET_MAPPING_SEQ
drop table UP_AGGR_TAB_MAPPING
drop table UP_AGGR_TAB_MAPPING_SEQ
drop table UP_CONCURRENT_USER_AGGR
drop table UP_CONCURRENT_USER_AGGR_SEQ
drop table UP_DATE_DIMENSION
drop table UP_DATE_DIMENSION_SEQ
drop table UP_EVENT_AGGR_CONF_GROUPS
drop table UP_EVENT_AGGR_CONF_GROUPS_EXC
drop table UP_EVENT_AGGR_CONF_GROUPS_INC
drop table UP_EVENT_AGGR_CONF_GROUPS_SEQ
drop table UP_EVENT_AGGR_CONF_INTRVL
drop table UP_EVENT_AGGR_CONF_INTRVL_EXC
drop table UP_EVENT_AGGR_CONF_INTRVL_INC
drop table UP_EVENT_AGGR_CONF_INTRVL_SEQ
drop table UP_EVENT_AGGR_STATUS
drop table UP_EVENT_AGGR_STATUS_SEQ
drop table UP_EVENT_SESSION
drop table UP_EVENT_SESSION_GROUPS
drop table UP_EVENT_SESSION_SEQ
drop table UP_LOGIN_EVENT_AGGR
drop table UP_LOGIN_EVENT_AGGR_SEQ
drop table UP_PORTLET_EXEC_AGGR
drop table UP_PORTLET_EXEC_AGGR_SEQ
drop table UP_PORTLET_LAYOUT_AGGR
drop table UP_PORTLET_LAYOUT_AGGR_SEQ
drop table UP_QUARTER_DETAIL
drop table UP_QUARTER_DETAIL_SEQ
drop table UP_SEARCH_REQ_AGGR
drop table UP_SEARCH_REQ_AGGR_SEQ
drop table UP_TAB_RENDER_AGGR
drop table UP_TAB_RENDER_AGGR_SEQ
drop table UP_TIME_DIMENSION
drop table UP_TIME_DIMENSION_SEQ
drop table UP_UNIQUE_STR
drop table UP_UNIQUE_STR_SEGMENT
drop table UP_UNIQUE_STR_SEGMENT_SEQ
drop table UP_UNIQUE_STR_SEGMENT__UIDS
drop table UP_UNIQUE_STR_SEQ
-- tables.xml create
alter table UP_ENTITY_TYPE add constraint UPET_NAME_UNQ unique (ENTITY_TYPE_NAME)
alter table UP_USER add constraint UPU_USER_NAME unique (USER_NAME)
create index UPG_GROUP_NAME_IDX on UP_GROUP (GROUP_NAME)
create index UPPERM_IDX__ACTIVITY on UP_PERMISSION (ACTIVITY)
create index UPPERM_IDX__OWNER on UP_PERMISSION (OWNER)
create index UPPERM_IDX__PETYPE on UP_PERMISSION (PERMISSION_TYPE)
create index UPPERM_IDX__PRKEY on UP_PERMISSION (PRINCIPAL_KEY)
create index UPPERM_IDX__PRTYPE on UP_PERMISSION (PRINCIPAL_TYPE)
create index UPPERM_IDX__TARGET on UP_PERMISSION (TARGET)
create table JGROUPSPING (own_addr varchar(200) not null, cluster_name varchar(200) not null, ping_data varbinary(5000), primary key (own_addr, cluster_name))
create table UP_ENTITY_CACHE_INVALIDATION (ENTITY_TYPE_ID int not null, ENTITY_KEY varchar(240) not null, INVALIDATION_TIME datetime not null, ENTITY_CACHE_ID int not null, primary key (ENTITY_TYPE_ID, ENTITY_KEY))
create table UP_ENTITY_LOCK (ENTITY_TYPE_ID int not null, ENTITY_KEY varchar(240) not null, LOCK_TYPE int not null, EXPIRATION_TIME datetime not null, LOCK_OWNER varchar(240) not null, primary key (ENTITY_TYPE_ID, ENTITY_KEY, LOCK_OWNER, EXPIRATION_TIME))
create table UP_ENTITY_TYPE (ENTITY_TYPE_ID int not null, ENTITY_TYPE_NAME varchar(255), DESCRIPTIVE_NAME varchar(255), primary key (ENTITY_TYPE_ID))
create table UP_GROUP (GROUP_ID varchar(240) not null, CREATOR_ID varchar(240) not null, ENTITY_TYPE_ID int not null, GROUP_NAME varchar(255) not null, DESCRIPTION varchar(255), primary key (GROUP_ID))
create table UP_GROUP_MEMBERSHIP (GROUP_ID varchar(200) not null, MEMBER_SERVICE varchar(80) not null, MEMBER_KEY varchar(200) not null, MEMBER_IS_GROUP varchar(1) not null, primary key (GROUP_ID, MEMBER_SERVICE, MEMBER_KEY, MEMBER_IS_GROUP))
create table UP_LAYOUT_PARAM (USER_ID int not null, LAYOUT_ID int not null, STRUCT_ID int not null, STRUCT_PARM_NM varchar(35) not null, STRUCT_PARM_VAL varchar(2000) not null, primary key (USER_ID, LAYOUT_ID, STRUCT_ID, STRUCT_PARM_NM))
create table UP_LAYOUT_STRUCT (USER_ID int not null, LAYOUT_ID int not null, STRUCT_ID int not null, NEXT_STRUCT_ID int, CHLD_STRUCT_ID int, EXTERNAL_ID varchar(64), CHAN_ID int, NAME varchar(35), TYPE varchar(35), HIDDEN varchar(1), IMMUTABLE varchar(1), UNREMOVABLE varchar(1), primary key (LAYOUT_ID, USER_ID, STRUCT_ID))
create table UP_PERMISSION (OWNER varchar(255), PRINCIPAL_TYPE int, PRINCIPAL_KEY varchar(255), ACTIVITY varchar(255), TARGET varchar(255), PERMISSION_TYPE varchar(255), EFFECTIVE datetime, EXPIRES datetime)
create table UP_SEQUENCE (SEQUENCE_NAME varchar(255) not null, SEQUENCE_VALUE int, primary key (SEQUENCE_NAME))
create table UP_USER (USER_ID int not null, USER_NAME varchar(100), USER_DFLT_LAY_ID int, NEXT_STRUCT_ID int, LST_CHAN_UPDT_DT datetime, primary key (USER_ID))
create table UP_USER_LAYOUT (LAYOUT_ID int not null, USER_ID int not null, LAYOUT_TITLE varchar(15), INIT_STRUCT_ID int, primary key (LAYOUT_ID, USER_ID))
create table UP_USER_LOCALE (USER_ID int not null, LOCALE varchar(64) not null, PRIORITY int, primary key (USER_ID, LOCALE))
create table UP_USER_PROFILE (USER_ID int not null, PROFILE_ID int not null, PROFILE_FNAME varchar(50) not null, PROFILE_NAME varchar(50), DESCRIPTION varchar(100), LAYOUT_ID int, STRUCTURE_SS_ID int, THEME_SS_ID int, primary key (USER_ID, PROFILE_ID))
-- tables.xml drop
drop table JGROUPSPING
drop table UP_ENTITY_CACHE_INVALIDATION
drop table UP_ENTITY_LOCK
drop table UP_ENTITY_TYPE
drop table UP_GROUP
drop table UP_GROUP_MEMBERSHIP
drop table UP_LAYOUT_PARAM
drop table UP_LAYOUT_STRUCT
drop table UP_PERMISSION
drop table UP_SEQUENCE
drop table UP_USER
drop table UP_USER_LAYOUT
drop table UP_USER_LOCALE
drop table UP_USER_PROFILE
