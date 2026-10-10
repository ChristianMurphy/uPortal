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
create sequence UP_DLM_EVALUATOR_SEQ start with 1 increment by 1
create sequence UP_MESSAGE_SEQ start with 1 increment by 5
create sequence UP_MUTEX_SEQ start with 1 increment by 1
create sequence UP_PAGS_GROUP_SEQ start with 1 increment by 5
create sequence UP_PAGS_TEST_GROUP_SEQ start with 1 increment by 5
create sequence UP_PAGS_TEST_SEQ start with 1 increment by 5
create sequence UP_PERMISSION_ACTIVITY_SEQ start with 1 increment by 5
create sequence UP_PERMISSION_OWNER_SEQ start with 1 increment by 1
create sequence UP_PERSON_ATTR_SEQ start with 1 increment by 10
create sequence UP_PERSON_DIR_SEQ start with 1 increment by 5
create sequence UP_PORTAL_COOKIES_SEQ start with 1 increment by 100
create sequence UP_PORTLET_COOKIES_SEQ start with 1 increment by 5
create sequence UP_PORTLET_DEF_PARAM_SEQ start with 1 increment by 5
create sequence UP_PORTLET_DEF_SEQ start with 1 increment by 5
create sequence UP_PORTLET_ENT_SEQ start with 1 increment by 10
create sequence UP_PORTLET_LIFECYCLE_SEQ start with 1 increment by 5
create sequence UP_PORTLET_PREFS_SEQ start with 1 increment by 10
create sequence UP_PORTLET_PREF_SEQ start with 1 increment by 10
create sequence UP_PORTLET_TYPE_SEQ start with 1 increment by 1
create sequence UP_PROFILE_SELECTION_SEQ start with 1 increment by 1
create sequence UP_SS_DESC_LAY_ATTR_SEQ start with 1 increment by 5
create sequence UP_SS_DESC_OUTPUT_PROP_SEQ start with 1 increment by 5
create sequence UP_SS_DESC_PARAM_SEQ start with 1 increment by 5
create sequence UP_SS_DESC_SEQ start with 1 increment by 5
create sequence UP_SS_USER_PREF_LAY_ATTR_SEQ start with 1 increment by 5
create sequence UP_SS_USER_PREF_SEQ start with 1 increment by 5
create sequence UP_TENANT_SEQ start with 1 increment by 1
create sequence UP_VERSION_SEQ start with 1 increment by 1
create table UP_DLM_EVALUATOR (EVALUATOR_TYPE varchar2(31 char) not null, EVALUATOR_ID number(19,0) not null, ENTITY_VERSION number(19,0), DESCRIPTION varchar2(255 char), FRAGMENT_NAME varchar2(255 char), OWNER_ID varchar2(255 char), PRECEDENCE double precision, ATTRIBUTE_MODE number(10,0), ATTRIBUTE_NAME varchar2(255 char), ATTRIBUTE_VALUE varchar2(255 char), EVALUATOR_MODE number(10,0), GROUP_KEY varchar2(255 char), GROUP_NAME varchar2(255 char), PROFILE_FNAME varchar2(255 char), PAREN_TYPE number(10,0), primary key (EVALUATOR_ID))
create table UP_DLM_EVALUATOR_PAREN (PAREN_EVAL_ID number(19,0) not null, CHILD_EVAL_ID number(19,0) not null, EVAL_INDEX number(10,0) not null, primary key (PAREN_EVAL_ID, EVAL_INDEX))
create table UP_JGROUPS_AUTH (SERVICE_NAME varchar2(100 char) not null, RANDOM_TOKEN varchar2(4000 char), primary key (SERVICE_NAME))
create table UP_MESSAGE (ID number(19,0) not null, CODE varchar2(128 char) not null, LOCALE varchar2(64 char) not null, VALUE varchar2(1024 char) not null, primary key (ID))
create table UP_MUTEX (MUTEX_ID number(19,0) not null, ENTITY_VERSION number(19,0), LOCK_UPDATE timestamp not null, LOCK_END timestamp not null, LOCK_START timestamp not null, LOCKED number(1,0) not null, MUTEX_NAME varchar2(200 char) not null, PREV_SERVER_ID varchar2(200 char), SERVER_ID varchar2(200 char), primary key (MUTEX_ID))
create table UP_PAGS_GROUP (PAGS_GROUP_ID number(19,0) not null, DESCRIPTION varchar2(500 char), ENTITY_VERSION number(19,0), NAME varchar2(500 char) not null, primary key (PAGS_GROUP_ID))
create table UP_PAGS_GROUP_MEMBERS (PAGS_GROUP_ID number(19,0) not null, PAGS_GROUP_MEMBER_ID number(19,0) not null, primary key (PAGS_GROUP_ID, PAGS_GROUP_MEMBER_ID))
create table UP_PAGS_TEST (PAGS_TEST_ID number(19,0) not null, ATTRIBUTE_NAME varchar2(500 char), ENTITY_VERSION number(19,0), TEST_VALUE varchar2(500 char), TESTER_CLASS varchar2(500 char), PAGS_TEST_GROUP_ID number(19,0) not null, primary key (PAGS_TEST_ID))
create table UP_PAGS_TEST_GROUP (PAGS_TEST_GROUP_ID number(19,0) not null, ENTITY_VERSION number(19,0), PAGS_GROUP_ID number(19,0) not null, primary key (PAGS_TEST_GROUP_ID))
create table UP_PERMISSION_ACTIVITY (ACTIVITY_ID number(19,0) not null, ACTIVITY_DESCRIPTION varchar2(255 char), ENTITY_VERSION number(19,0), ACTIVITY_FNAME varchar2(128 char) not null, ACTIVITY_NAME varchar2(128 char) not null, OWNER_TARGET_PROVIDER varchar2(255 char) not null, OWNER_ID number(19,0), primary key (ACTIVITY_ID))
create table UP_PERMISSION_OWNER (OWNER_ID number(19,0) not null, OWNER_DESCRIPTION varchar2(255 char), ENTITY_VERSION number(19,0), OWNER_FNAME varchar2(128 char) not null, OWNER_NAME varchar2(128 char) not null, primary key (OWNER_ID))
create table UP_PERSON_ATTR (id number(10,0) not null, ENTITY_VERSION number(19,0), ATTR_NAME varchar2(255 char) not null, USER_DIR_ID number(19,0) not null, primary key (id))
create table UP_PERSON_ATTR_VALUES (ATTR_ID number(10,0) not null, ATTR_VALUE varchar2(255 char), VALUE_ORDER number(10,0) not null, primary key (ATTR_ID, VALUE_ORDER))
create table UP_PERSON_DIR (USER_DIR_ID number(19,0) not null, ENTITY_VERSION number(19,0), LST_PSWD_CGH_DT timestamp, USER_NAME varchar2(100 char) not null, ENCRPTD_PSWD varchar2(256 char), primary key (USER_DIR_ID))
create table UP_PORTAL_COOKIES (PORTAL_COOKIE_ID number(19,0) not null, CREATED timestamp not null, ENTITY_VERSION number(19,0), EXPIRES timestamp not null, COOKIE_VALUE varchar2(100 char) not null, primary key (PORTAL_COOKIE_ID))
create table UP_PORTLET_COOKIES (PORTLET_COOKIE_ID number(19,0) not null, COOKIE_COMMENT varchar2(1000 char), COOKIE_DOMAIN varchar2(500 char), ENTITY_VERSION number(19,0), EXPIRES timestamp not null, COOKIE_NAME varchar2(500 char) not null, COOKIE_PATH varchar2(1000 char), SECURE number(1,0) not null, COOKIE_VALUE varchar2(1000 char) not null, VERSION number(10,0) not null, portalCookie_PORTAL_COOKIE_ID number(19,0), PORTAL_COOKIE_ID number(19,0), primary key (PORTLET_COOKIE_ID))
create table UP_PORTLET_DEF (PORTLET_DEF_ID number(19,0) not null, ACTION_TIMEOUT number(10,0), PORTLET_DESC varchar2(255 char), ENTITY_VERSION number(19,0), EVENT_TIMEOUT number(10,0), PORTLET_FNAME varchar2(255 char) not null, PORTLET_NAME varchar2(128 char) not null, PORTLET_FRAMEWORK number(1,0) not null, PORTLET_APPLICATION_NAME varchar2(255 char) not null, PORTLET_WEBAPP_NAME varchar2(255 char), AVG_RATING double precision, RENDER_TIMEOUT number(10,0), RESOURCE_TIMEOUT number(10,0), PORTLET_TIMEOUT number(10,0) not null, PORTLET_TITLE varchar2(128 char) not null, AVG_RATING_USER_COUNT number(19,0), PORTLET_PREFS_ID number(19,0) not null, PORTLET_TYPE_ID number(10,0) not null, primary key (PORTLET_DEF_ID))
create table UP_PORTLET_DEF_MDATA (PORTLET_ID number(19,0) not null, PORTLET_DESC varchar2(255 char), PORTLET_NAME varchar2(128 char), PORTLET_TITLE varchar2(128 char), LOCALE varchar2(64 char) not null, primary key (PORTLET_ID, LOCALE))
create table UP_PORTLET_DEF_PARAM (PARAM_ID number(19,0) not null, PARAM_DESC varchar2(255 char), PARAM_NAME varchar2(255 char) not null, PARAM_VAL varchar2(2000 char), PORTLET_DEF_ID number(19,0) not null, primary key (PARAM_ID))
create table UP_PORTLET_ENT (PORTLET_ENT_ID number(19,0) not null, ENTITY_VERSION number(19,0), LAYOUT_NODE_ID varchar2(255 char) not null, USER_ID number(10,0) not null, PORTLET_DEF_ID number(19,0) not null, PORTLET_PREFS_ID number(19,0) not null, primary key (PORTLET_ENT_ID))
create table UP_PORTLET_ENT__STATES (PORTLET_ENT_ID number(19,0) not null, WINDOW_STATE varchar2(255 char), STYLESHEET_DESCRIPTOR_ID number(19,0) not null, primary key (PORTLET_ENT_ID, STYLESHEET_DESCRIPTOR_ID))
create table UP_PORTLET_LIFECYCLE (ENTRY_ID number(19,0) not null, ENTRY_DATE timestamp, LIFECYCLE_STATE_ID number(10,0), USER_ID number(10,0), PORTLET_DEF_ID number(19,0) not null, primary key (ENTRY_ID))
create table UP_PORTLET_LIST (ID varchar2(255 char) not null, CREATED_BY varchar2(255 char) not null, CREATED_ON timestamp not null, NAME varchar2(255 char) not null, OWNER_USERNAME varchar2(255 char) not null, UPDATED_BY varchar2(255 char) not null, UPDATED_ON timestamp not null, primary key (ID))
create table UP_PORTLET_LIST_ITEM (ID varchar2(255 char) not null, ENTITY_ID varchar2(255 char) not null, LIST_ORDER number(10,0) not null, LIST_ID varchar2(255 char) not null, primary key (ID))
create table UP_PORTLET_PREF (PORTLET_PREF_ID number(19,0) not null, ENTITY_VERSION number(19,0), PREF_NAME clob, NULL_VALUES number(1,0) not null, READ_ONLY number(1,0) not null, PORTLET_PREFS_ID number(19,0), PREF_ORDER number(10,0), primary key (PORTLET_PREF_ID))
create table UP_PORTLET_PREFS (PORTLET_PREFS_ID number(19,0) not null, ENTITY_VERSION number(19,0), primary key (PORTLET_PREFS_ID))
create table UP_PORTLET_PREF_VALUES (PORTLET_PREF_ID number(19,0) not null, PREF_VALUE clob, VALUE_ORDER number(10,0) not null, primary key (PORTLET_PREF_ID, VALUE_ORDER))
create table UP_PORTLET_RATING (USER_NAME varchar2(255 char) not null, RATING number(10,0), REVIEW varchar2(160 char), PORTLET_ID number(19,0), primary key (PORTLET_ID, USER_NAME))
create table UP_PORTLET_TYPE (TYPE_ID number(10,0) not null, TYPE_DEF_URI varchar2(255 char) not null, TYPE_DESCR varchar2(2000 char), ENTITY_VERSION number(19,0), TYPE_NAME varchar2(70 char), primary key (TYPE_ID))
create table UP_PROFILE_SELECTION (SELECTION_ID number(10,0) not null, ENTITY_VERSION number(19,0), PROFILE_FNAME varchar2(70 char) not null, USERNAME varchar2(70 char) not null, primary key (SELECTION_ID))
create table UP_SS_DESC (SS_DESC_ID number(19,0) not null, DESCRIPTION varchar2(2000 char), ENTITY_VERSION number(19,0), SS_NAME varchar2(100 char) not null, STYLESHEET varchar2(2000 char) not null, URL_SYNTAX_HELPER_NAME varchar2(100 char), primary key (SS_DESC_ID))
create table UP_SS_DESC_LAY_ATTR (SS_DESC_LAYOUT_ATTR_ID number(19,0) not null, DEFAULT_VALUE varchar2(500 char), DESCRIPTION varchar2(2000 char), DATA_NAME varchar2(100 char) not null, PERSISTENCE_SCOPE varchar2(255 char) not null, ENTITY_VERSION number(19,0), SS_DESC_ID number(19,0) not null, primary key (SS_DESC_LAYOUT_ATTR_ID))
create table UP_SS_DESC_LAY_ATTR_ELMS (ATTR_ID number(19,0) not null, ELEMENT_NAME varchar2(255 char))
create table UP_SS_DESC_OUTPUT_PROP (SS_DESC_LAYOUT_ATTR_ID number(19,0) not null, DEFAULT_VALUE varchar2(500 char), DESCRIPTION varchar2(2000 char), DATA_NAME varchar2(100 char) not null, PERSISTENCE_SCOPE varchar2(255 char) not null, ENTITY_VERSION number(19,0), SS_DESC_ID number(19,0) not null, primary key (SS_DESC_LAYOUT_ATTR_ID))
create table UP_SS_DESC_PARAM (SS_DESC_PARAM_ID number(19,0) not null, DEFAULT_VALUE varchar2(500 char), DESCRIPTION varchar2(2000 char), DATA_NAME varchar2(100 char) not null, PERSISTENCE_SCOPE varchar2(255 char) not null, ENTITY_VERSION number(19,0), SS_DESC_ID number(19,0) not null, primary key (SS_DESC_PARAM_ID))
create table UP_SS_USER_PREF (SS_USER_PREF_ID number(19,0) not null, ENTITY_VERSION number(19,0), PROFILE_ID number(10,0) not null, USER_ID number(10,0) not null, UP_SS_DESCRIPTOR_ID number(19,0) not null, primary key (SS_USER_PREF_ID))
create table UP_SS_USER_PREF_LAY_ATTR (UP_SS_USER_PREF_LAY_ATTR_ID number(19,0) not null, ENTITY_VERSION number(19,0), NODE_ID varchar2(200 char) not null, SS_USER_PREF_ID number(19,0) not null, primary key (UP_SS_USER_PREF_LAY_ATTR_ID))
create table UP_SS_USER_PREF_LAY_ATTR_VAL (UP_SS_USER_PREF_LAY_ATTR_ID number(19,0) not null, ATTR_VALUE varchar2(2000 char) not null, ATTR_NAME varchar2(500 char) not null, primary key (UP_SS_USER_PREF_LAY_ATTR_ID, ATTR_NAME))
create table UP_SS_USER_PREF_OUTPUT_PROP (SS_USER_PREF_ID number(19,0) not null, PROP_VALUE varchar2(2000 char) not null, PROP_NAME varchar2(500 char) not null, primary key (SS_USER_PREF_ID, PROP_NAME))
create table UP_SS_USER_PREF_PARAM (SS_USER_PREF_ID number(19,0) not null, PARAM_VALUE varchar2(2000 char) not null, PARAM_NAME varchar2(500 char) not null, primary key (SS_USER_PREF_ID, PARAM_NAME))
create table UP_TENANT (TENANT_ID number(19,0) not null, TENANT_FNAME varchar2(128 char) not null, TENANT_NAME varchar2(255 char) not null, primary key (TENANT_ID))
create table UP_TENANT_ATTRIBUTES (TENANT_ID number(19,0) not null, ATTR_VALUE varchar2(2000 char) not null, ATTR_NAME varchar2(500 char) not null, primary key (TENANT_ID, ATTR_NAME))
create table UP_VERSION (VERSION_ID number(19,0) not null, ENTITY_VERSION number(19,0), LOCAL_VER number(10,0), MAJOR_VER number(10,0) not null, MINOR_VER number(10,0) not null, PATCH_VER number(10,0) not null, PRODUCT varchar2(128 char) not null, primary key (VERSION_ID))
-- PortalDb drop
drop sequence UP_DLM_EVALUATOR_SEQ
drop sequence UP_MESSAGE_SEQ
drop sequence UP_MUTEX_SEQ
drop sequence UP_PAGS_GROUP_SEQ
drop sequence UP_PAGS_TEST_GROUP_SEQ
drop sequence UP_PAGS_TEST_SEQ
drop sequence UP_PERMISSION_ACTIVITY_SEQ
drop sequence UP_PERMISSION_OWNER_SEQ
drop sequence UP_PERSON_ATTR_SEQ
drop sequence UP_PERSON_DIR_SEQ
drop sequence UP_PORTAL_COOKIES_SEQ
drop sequence UP_PORTLET_COOKIES_SEQ
drop sequence UP_PORTLET_DEF_PARAM_SEQ
drop sequence UP_PORTLET_DEF_SEQ
drop sequence UP_PORTLET_ENT_SEQ
drop sequence UP_PORTLET_LIFECYCLE_SEQ
drop sequence UP_PORTLET_PREFS_SEQ
drop sequence UP_PORTLET_PREF_SEQ
drop sequence UP_PORTLET_TYPE_SEQ
drop sequence UP_PROFILE_SELECTION_SEQ
drop sequence UP_SS_DESC_LAY_ATTR_SEQ
drop sequence UP_SS_DESC_OUTPUT_PROP_SEQ
drop sequence UP_SS_DESC_PARAM_SEQ
drop sequence UP_SS_DESC_SEQ
drop sequence UP_SS_USER_PREF_LAY_ATTR_SEQ
drop sequence UP_SS_USER_PREF_SEQ
drop sequence UP_TENANT_SEQ
drop sequence UP_VERSION_SEQ
drop table UP_DLM_EVALUATOR cascade constraints
drop table UP_DLM_EVALUATOR_PAREN cascade constraints
drop table UP_JGROUPS_AUTH cascade constraints
drop table UP_MESSAGE cascade constraints
drop table UP_MUTEX cascade constraints
drop table UP_PAGS_GROUP cascade constraints
drop table UP_PAGS_GROUP_MEMBERS cascade constraints
drop table UP_PAGS_TEST cascade constraints
drop table UP_PAGS_TEST_GROUP cascade constraints
drop table UP_PERMISSION_ACTIVITY cascade constraints
drop table UP_PERMISSION_OWNER cascade constraints
drop table UP_PERSON_ATTR cascade constraints
drop table UP_PERSON_ATTR_VALUES cascade constraints
drop table UP_PERSON_DIR cascade constraints
drop table UP_PORTAL_COOKIES cascade constraints
drop table UP_PORTLET_COOKIES cascade constraints
drop table UP_PORTLET_DEF cascade constraints
drop table UP_PORTLET_DEF_MDATA cascade constraints
drop table UP_PORTLET_DEF_PARAM cascade constraints
drop table UP_PORTLET_ENT cascade constraints
drop table UP_PORTLET_ENT__STATES cascade constraints
drop table UP_PORTLET_LIFECYCLE cascade constraints
drop table UP_PORTLET_LIST cascade constraints
drop table UP_PORTLET_LIST_ITEM cascade constraints
drop table UP_PORTLET_PREF cascade constraints
drop table UP_PORTLET_PREFS cascade constraints
drop table UP_PORTLET_PREF_VALUES cascade constraints
drop table UP_PORTLET_RATING cascade constraints
drop table UP_PORTLET_TYPE cascade constraints
drop table UP_PROFILE_SELECTION cascade constraints
drop table UP_SS_DESC cascade constraints
drop table UP_SS_DESC_LAY_ATTR cascade constraints
drop table UP_SS_DESC_LAY_ATTR_ELMS cascade constraints
drop table UP_SS_DESC_OUTPUT_PROP cascade constraints
drop table UP_SS_DESC_PARAM cascade constraints
drop table UP_SS_USER_PREF cascade constraints
drop table UP_SS_USER_PREF_LAY_ATTR cascade constraints
drop table UP_SS_USER_PREF_LAY_ATTR_VAL cascade constraints
drop table UP_SS_USER_PREF_OUTPUT_PROP cascade constraints
drop table UP_SS_USER_PREF_PARAM cascade constraints
drop table UP_TENANT cascade constraints
drop table UP_TENANT_ATTRIBUTES cascade constraints
drop table UP_VERSION cascade constraints
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
create sequence UP_ANALYTICS_EVENTS_SEQ start with 1 increment by 1000
create sequence UP_RAW_EVENTS_SEQ start with 1 increment by 1000
create table UP_ANALYTICS_EVENTS (EVENT_ID number(19,0) not null, EVENT_DATA clob not null, SESSION_ID varchar2(500 char) not null, EVENT_TYPE varchar2(200 char) not null, SERVER_ID varchar2(200 char) not null, TIMESTAMP timestamp not null, USER_NAME varchar2(100 char) not null, primary key (EVENT_ID))
create table UP_RAW_EVENTS (EVENT_ID number(19,0) not null, AGGREGATED number(1,0), ERROR_AGGR number(1,0), EVENT_DATA clob not null, SESSION_ID varchar2(500 char) not null, EVENT_TYPE varchar2(200 char) not null, SERVER_ID varchar2(200 char) not null, TIMESTAMP timestamp not null, USER_NAME varchar2(100 char) not null, primary key (EVENT_ID))
-- RawEventsDb drop
drop sequence UP_ANALYTICS_EVENTS_SEQ
drop sequence UP_RAW_EVENTS_SEQ
drop table UP_ANALYTICS_EVENTS cascade constraints
drop table UP_RAW_EVENTS cascade constraints
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
create sequence UP_ACADEMIC_TERM_DETAIL_SEQ start with 1 increment by 1
create sequence UP_AGGR_GROUP_MAPPING_SEQ start with 1 increment by 10
create sequence UP_AGGR_PORTLET_MAPPING_SEQ start with 1 increment by 10
create sequence UP_AGGR_TAB_MAPPING_SEQ start with 1 increment by 10
create sequence UP_CONCURRENT_USER_AGGR_SEQ start with 1 increment by 1000
create sequence UP_DATE_DIMENSION_SEQ start with 1 increment by 1
create sequence UP_EVENT_AGGR_CONF_GROUPS_SEQ start with 1 increment by 1
create sequence UP_EVENT_AGGR_CONF_INTRVL_SEQ start with 1 increment by 1
create sequence UP_EVENT_AGGR_STATUS_SEQ start with 1 increment by 10
create sequence UP_EVENT_SESSION_SEQ start with 1 increment by 100
create sequence UP_LOGIN_EVENT_AGGR_SEQ start with 1 increment by 1000
create sequence UP_PORTLET_EXEC_AGGR_SEQ start with 1 increment by 10000
create sequence UP_PORTLET_LAYOUT_AGGR_SEQ start with 1 increment by 1000
create sequence UP_QUARTER_DETAIL_SEQ start with 1 increment by 1
create sequence UP_SEARCH_REQ_AGGR_SEQ start with 1 increment by 1000
create sequence UP_TAB_RENDER_AGGR_SEQ start with 1 increment by 5000
create sequence UP_TIME_DIMENSION_SEQ start with 1 increment by 1
create sequence UP_UNIQUE_STR_SEGMENT_SEQ start with 1 increment by 1000
create sequence UP_UNIQUE_STR_SEQ start with 1 increment by 1000
create table UP_ACADEMIC_TERM_DETAIL (TERM_ID number(19,0) not null, TERM_END timestamp not null, TERM_START timestamp not null, TERM_NAME varchar2(255 char) not null, primary key (TERM_ID))
create table UP_AGGR_GROUP_MAPPING (ID number(19,0) not null, GROUP_NAME varchar2(200 char) not null, GROUP_SERVICE varchar2(200 char) not null, primary key (ID))
create table UP_AGGR_PORTLET_MAPPING (ID number(19,0) not null, PORTLET_FNAME varchar2(255 char) not null, PORTLET_NAME varchar2(128 char) not null, primary key (ID))
create table UP_AGGR_TAB_MAPPING (ID number(19,0) not null, FRAGMENT_NAME varchar2(200 char) not null, TAB_NAME varchar2(200 char) not null, primary key (ID))
create table UP_CONCURRENT_USER_AGGR (ID number(19,0) not null, DURATION number(10,0) not null, AGGR_INTERVAL varchar2(255 char) not null, CONCURRENT_USERS number(10,0) not null, AGGR_GROUP_ID number(19,0) not null, DATE_DIMENSION_ID number(19,0) not null, TIME_DIMENSION_ID number(19,0) not null, UNIQUE_STRINGS_ID number(19,0), primary key (ID))
create table UP_DATE_DIMENSION (DATE_ID number(19,0) not null, DD_DATE date not null, DD_DAY number(10,0) not null, DD_MONTH number(10,0) not null, DD_QUARTER number(10,0) not null, DD_TERM varchar2(200 char), DD_WEEK number(10,0) not null, DD_YEAR number(10,0) not null, primary key (DATE_ID))
create table UP_EVENT_AGGR_CONF_GROUPS (ID number(19,0) not null, AGGREGATOR_TYPE varchar2(255 char) not null, ENTITY_VERSION number(19,0), primary key (ID))
create table UP_EVENT_AGGR_CONF_GROUPS_EXC (UP_EVENT_AGGR_CONF_GROUPS_ID number(19,0) not null, GROUP_ID number(19,0) not null, primary key (UP_EVENT_AGGR_CONF_GROUPS_ID, GROUP_ID))
create table UP_EVENT_AGGR_CONF_GROUPS_INC (UP_EVENT_AGGR_CONF_GROUPS_ID number(19,0) not null, GROUP_ID number(19,0) not null, primary key (UP_EVENT_AGGR_CONF_GROUPS_ID, GROUP_ID))
create table UP_EVENT_AGGR_CONF_INTRVL (ID number(19,0) not null, AGGREGATOR_TYPE varchar2(255 char) not null, ENTITY_VERSION number(19,0), primary key (ID))
create table UP_EVENT_AGGR_CONF_INTRVL_EXC (UP_EVENT_AGGR_CONF_INTRVL_ID number(19,0) not null, AGGR_INTERVAL varchar2(255 char))
create table UP_EVENT_AGGR_CONF_INTRVL_INC (UP_EVENT_AGGR_CONF_INTRVL_ID number(19,0) not null, AGGR_INTERVAL varchar2(255 char))
create table UP_EVENT_AGGR_STATUS (ID number(19,0) not null, ENTITY_VERSION number(19,0), LAST_END timestamp, LAST_EVENT_DATE timestamp, LAST_START timestamp, PROCESSING_TYPE varchar2(255 char) not null, SERVER_NAME varchar2(200 char), primary key (ID))
create table UP_EVENT_SESSION (ID number(19,0) not null, SESSION_ID varchar2(500 char) not null, LAST_ACCESSED timestamp not null, primary key (ID))
create table UP_EVENT_SESSION_GROUPS (UP_EVENT_SESSION_ID number(19,0) not null, GROUP_ID number(19,0) not null, primary key (UP_EVENT_SESSION_ID, GROUP_ID))
create table UP_LOGIN_EVENT_AGGR (ID number(19,0) not null, DURATION number(10,0) not null, AGGR_INTERVAL varchar2(255 char) not null, LOGIN_COUNT number(10,0) not null, UNIQUE_LOGIN_COUNT number(10,0) not null, AGGR_GROUP_ID number(19,0) not null, DATE_DIMENSION_ID number(19,0) not null, TIME_DIMENSION_ID number(19,0) not null, UNIQUE_STRINGS_ID number(19,0), primary key (ID))
create table UP_PORTLET_EXEC_AGGR (ID number(19,0) not null, DURATION number(10,0) not null, AGGR_INTERVAL varchar2(255 char) not null, STATS_COMPLETE number(1,0) not null, TIME_COUNT number(10,0) not null, GEOMETRIC_MEAN_TIME double precision not null, MAX_TIME double precision not null, MEAN_TIME double precision not null, MIN_TIME double precision not null, POPULATION_VARIANCE_TIME double precision not null, SECOND_MOMENT_TIME double precision not null, STD_DEVIATION_TIME double precision not null, CM_MAX_N number(19,0), CM_MAX_VALUE double precision, CM_MIN_N number(19,0), CM_MIN_VALUE double precision, CM_2MMNT_M2 double precision, CM_1MMNT_DEV double precision, CM_1MMNT_M1 double precision, CM_1MMNT_N number(19,0), CM_1MMNT_NDEV double precision, CM_SUM_N number(19,0), CM_SUM_VALUE double precision, CM_SUMOLOGS_N number(10,0), CM_SUMOLOGS_VALUE double precision, CM_SUMOSQRS_N number(19,0), CM_SUMOSQRS_VALUE double precision, SUM_TIME double precision not null, SUM_OF_LOGS_TIME double precision not null, SUMSQ_TIME double precision not null, VARIANCE_TIME double precision not null, EXECUTION_TYPE varchar2(50 char) not null, AGGR_GROUP_ID number(19,0) not null, DATE_DIMENSION_ID number(19,0) not null, TIME_DIMENSION_ID number(19,0) not null, AGGR_PORTLET_ID number(19,0) not null, primary key (ID))
create table UP_PORTLET_LAYOUT_AGGR (ID number(19,0) not null, DURATION number(10,0) not null, AGGR_INTERVAL varchar2(255 char) not null, ADD_COUNT number(10,0) not null, STATS_COMPLETE number(1,0) not null, DEL_COUNT number(10,0) not null, MOVE_COUNT number(10,0) not null, AGGR_GROUP_ID number(19,0) not null, DATE_DIMENSION_ID number(19,0) not null, TIME_DIMENSION_ID number(19,0) not null, AGGR_PORTLET_ID number(19,0) not null, primary key (ID))
create table UP_QUARTER_DETAIL (QUARTER_ID number(19,0) not null, QUARTER_END varchar2(255 char) not null, QUARTER_NUMBER number(10,0) not null, QUARTER_START varchar2(255 char) not null, primary key (QUARTER_ID))
create table UP_SEARCH_REQ_AGGR (ID number(19,0) not null, DURATION number(10,0) not null, AGGR_INTERVAL varchar2(255 char) not null, STATS_COMPLETE number(1,0) not null, SEARCH_COUNT number(10,0) not null, SEARCH_TERM varchar2(255 char) not null, AGGR_GROUP_ID number(19,0) not null, DATE_DIMENSION_ID number(19,0) not null, TIME_DIMENSION_ID number(19,0) not null, primary key (ID))
create table UP_TAB_RENDER_AGGR (ID number(19,0) not null, DURATION number(10,0) not null, AGGR_INTERVAL varchar2(255 char) not null, STATS_COMPLETE number(1,0) not null, TIME_COUNT number(10,0) not null, GEOMETRIC_MEAN_TIME double precision not null, MAX_TIME double precision not null, MEAN_TIME double precision not null, MIN_TIME double precision not null, POPULATION_VARIANCE_TIME double precision not null, SECOND_MOMENT_TIME double precision not null, STD_DEVIATION_TIME double precision not null, CM_MAX_N number(19,0), CM_MAX_VALUE double precision, CM_MIN_N number(19,0), CM_MIN_VALUE double precision, CM_2MMNT_M2 double precision, CM_1MMNT_DEV double precision, CM_1MMNT_M1 double precision, CM_1MMNT_N number(19,0), CM_1MMNT_NDEV double precision, CM_SUM_N number(19,0), CM_SUM_VALUE double precision, CM_SUMOLOGS_N number(10,0), CM_SUMOLOGS_VALUE double precision, CM_SUMOSQRS_N number(19,0), CM_SUMOSQRS_VALUE double precision, SUM_TIME double precision not null, SUM_OF_LOGS_TIME double precision not null, SUMSQ_TIME double precision not null, VARIANCE_TIME double precision not null, AGGR_GROUP_ID number(19,0) not null, DATE_DIMENSION_ID number(19,0) not null, TIME_DIMENSION_ID number(19,0) not null, AGGR_TAB_ID number(19,0) not null, primary key (ID))
create table UP_TIME_DIMENSION (TIME_ID number(19,0) not null, TD_FIVE_MINUTE_INCREMENT number(10,0) not null, TD_HOUR number(10,0) not null, TD_MINUTE number(10,0) not null, TD_TIME date not null, primary key (TIME_ID))
create table UP_UNIQUE_STR (UNIQUE_STR_ID number(19,0) not null, primary key (UNIQUE_STR_ID))
create table UP_UNIQUE_STR_SEGMENT (UNIQUE_STR_SEGMENT_ID number(19,0) not null, UNIQUE_STR_ID number(19,0) not null, primary key (UNIQUE_STR_SEGMENT_ID))
create table UP_UNIQUE_STR_SEGMENT__UIDS (UNIQUE_STR_SEGMENT_ID number(19,0) not null, UNIQUE_STR varchar2(255 char) not null, primary key (UNIQUE_STR_SEGMENT_ID, UNIQUE_STR))
-- AggrEventsDb drop
drop sequence UP_ACADEMIC_TERM_DETAIL_SEQ
drop sequence UP_AGGR_GROUP_MAPPING_SEQ
drop sequence UP_AGGR_PORTLET_MAPPING_SEQ
drop sequence UP_AGGR_TAB_MAPPING_SEQ
drop sequence UP_CONCURRENT_USER_AGGR_SEQ
drop sequence UP_DATE_DIMENSION_SEQ
drop sequence UP_EVENT_AGGR_CONF_GROUPS_SEQ
drop sequence UP_EVENT_AGGR_CONF_INTRVL_SEQ
drop sequence UP_EVENT_AGGR_STATUS_SEQ
drop sequence UP_EVENT_SESSION_SEQ
drop sequence UP_LOGIN_EVENT_AGGR_SEQ
drop sequence UP_PORTLET_EXEC_AGGR_SEQ
drop sequence UP_PORTLET_LAYOUT_AGGR_SEQ
drop sequence UP_QUARTER_DETAIL_SEQ
drop sequence UP_SEARCH_REQ_AGGR_SEQ
drop sequence UP_TAB_RENDER_AGGR_SEQ
drop sequence UP_TIME_DIMENSION_SEQ
drop sequence UP_UNIQUE_STR_SEGMENT_SEQ
drop sequence UP_UNIQUE_STR_SEQ
drop table UP_ACADEMIC_TERM_DETAIL cascade constraints
drop table UP_AGGR_GROUP_MAPPING cascade constraints
drop table UP_AGGR_PORTLET_MAPPING cascade constraints
drop table UP_AGGR_TAB_MAPPING cascade constraints
drop table UP_CONCURRENT_USER_AGGR cascade constraints
drop table UP_DATE_DIMENSION cascade constraints
drop table UP_EVENT_AGGR_CONF_GROUPS cascade constraints
drop table UP_EVENT_AGGR_CONF_GROUPS_EXC cascade constraints
drop table UP_EVENT_AGGR_CONF_GROUPS_INC cascade constraints
drop table UP_EVENT_AGGR_CONF_INTRVL cascade constraints
drop table UP_EVENT_AGGR_CONF_INTRVL_EXC cascade constraints
drop table UP_EVENT_AGGR_CONF_INTRVL_INC cascade constraints
drop table UP_EVENT_AGGR_STATUS cascade constraints
drop table UP_EVENT_SESSION cascade constraints
drop table UP_EVENT_SESSION_GROUPS cascade constraints
drop table UP_LOGIN_EVENT_AGGR cascade constraints
drop table UP_PORTLET_EXEC_AGGR cascade constraints
drop table UP_PORTLET_LAYOUT_AGGR cascade constraints
drop table UP_QUARTER_DETAIL cascade constraints
drop table UP_SEARCH_REQ_AGGR cascade constraints
drop table UP_TAB_RENDER_AGGR cascade constraints
drop table UP_TIME_DIMENSION cascade constraints
drop table UP_UNIQUE_STR cascade constraints
drop table UP_UNIQUE_STR_SEGMENT cascade constraints
drop table UP_UNIQUE_STR_SEGMENT__UIDS cascade constraints
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
create table JGROUPSPING (own_addr varchar2(200 char) not null, cluster_name varchar2(200 char) not null, ping_data long raw, primary key (own_addr, cluster_name))
create table UP_ENTITY_CACHE_INVALIDATION (ENTITY_TYPE_ID number(10,0) not null, ENTITY_KEY varchar2(240 char) not null, INVALIDATION_TIME timestamp not null, ENTITY_CACHE_ID number(10,0) not null, primary key (ENTITY_TYPE_ID, ENTITY_KEY))
create table UP_ENTITY_LOCK (ENTITY_TYPE_ID number(10,0) not null, ENTITY_KEY varchar2(240 char) not null, LOCK_TYPE number(10,0) not null, EXPIRATION_TIME timestamp not null, LOCK_OWNER varchar2(240 char) not null, primary key (ENTITY_TYPE_ID, ENTITY_KEY, LOCK_OWNER, EXPIRATION_TIME))
create table UP_ENTITY_TYPE (ENTITY_TYPE_ID number(10,0) not null, ENTITY_TYPE_NAME varchar2(255 char), DESCRIPTIVE_NAME varchar2(255 char), primary key (ENTITY_TYPE_ID))
create table UP_GROUP (GROUP_ID varchar2(240 char) not null, CREATOR_ID varchar2(240 char) not null, ENTITY_TYPE_ID number(10,0) not null, GROUP_NAME varchar2(255 char) not null, DESCRIPTION varchar2(255 char), primary key (GROUP_ID))
create table UP_GROUP_MEMBERSHIP (GROUP_ID varchar2(200 char) not null, MEMBER_SERVICE varchar2(80 char) not null, MEMBER_KEY varchar2(200 char) not null, MEMBER_IS_GROUP varchar2(1 char) not null, primary key (GROUP_ID, MEMBER_SERVICE, MEMBER_KEY, MEMBER_IS_GROUP))
create table UP_LAYOUT_PARAM (USER_ID number(10,0) not null, LAYOUT_ID number(10,0) not null, STRUCT_ID number(10,0) not null, STRUCT_PARM_NM varchar2(35 char) not null, STRUCT_PARM_VAL varchar2(2000 char) not null, primary key (USER_ID, LAYOUT_ID, STRUCT_ID, STRUCT_PARM_NM))
create table UP_LAYOUT_STRUCT (USER_ID number(10,0) not null, LAYOUT_ID number(10,0) not null, STRUCT_ID number(10,0) not null, NEXT_STRUCT_ID number(10,0), CHLD_STRUCT_ID number(10,0), EXTERNAL_ID varchar2(64 char), CHAN_ID number(10,0), NAME varchar2(35 char), TYPE varchar2(35 char), HIDDEN varchar2(1 char), IMMUTABLE varchar2(1 char), UNREMOVABLE varchar2(1 char), primary key (LAYOUT_ID, USER_ID, STRUCT_ID))
create table UP_PERMISSION (OWNER varchar2(255 char), PRINCIPAL_TYPE number(10,0), PRINCIPAL_KEY varchar2(255 char), ACTIVITY varchar2(255 char), TARGET varchar2(255 char), PERMISSION_TYPE varchar2(255 char), EFFECTIVE timestamp, EXPIRES timestamp)
create table UP_SEQUENCE (SEQUENCE_NAME varchar2(255 char) not null, SEQUENCE_VALUE number(10,0), primary key (SEQUENCE_NAME))
create table UP_USER (USER_ID number(10,0) not null, USER_NAME varchar2(100 char), USER_DFLT_LAY_ID number(10,0), NEXT_STRUCT_ID number(10,0), LST_CHAN_UPDT_DT timestamp, primary key (USER_ID))
create table UP_USER_LAYOUT (LAYOUT_ID number(10,0) not null, USER_ID number(10,0) not null, LAYOUT_TITLE varchar2(15 char), INIT_STRUCT_ID number(10,0), primary key (LAYOUT_ID, USER_ID))
create table UP_USER_LOCALE (USER_ID number(10,0) not null, LOCALE varchar2(64 char) not null, PRIORITY number(10,0), primary key (USER_ID, LOCALE))
create table UP_USER_PROFILE (USER_ID number(10,0) not null, PROFILE_ID number(10,0) not null, PROFILE_FNAME varchar2(50 char) not null, PROFILE_NAME varchar2(50 char), DESCRIPTION varchar2(100 char), LAYOUT_ID number(10,0), STRUCTURE_SS_ID number(10,0), THEME_SS_ID number(10,0), primary key (USER_ID, PROFILE_ID))
-- tables.xml drop
drop table JGROUPSPING cascade constraints
drop table UP_ENTITY_CACHE_INVALIDATION cascade constraints
drop table UP_ENTITY_LOCK cascade constraints
drop table UP_ENTITY_TYPE cascade constraints
drop table UP_GROUP cascade constraints
drop table UP_GROUP_MEMBERSHIP cascade constraints
drop table UP_LAYOUT_PARAM cascade constraints
drop table UP_LAYOUT_STRUCT cascade constraints
drop table UP_PERMISSION cascade constraints
drop table UP_SEQUENCE cascade constraints
drop table UP_USER cascade constraints
drop table UP_USER_LAYOUT cascade constraints
drop table UP_USER_LOCALE cascade constraints
drop table UP_USER_PROFILE cascade constraints
