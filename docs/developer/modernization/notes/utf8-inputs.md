# Text inputs accept any Unicode text (MOD-D49)

Branch `ccr-43ba0785-hzlrj5-utf8-inputs` in uPortal, made from `ccr-43ba0785-hzlrj5` at
`f6be649`. The coordinator merges it. LOG row numbers for new findings: MOD-I270 to MOD-I279.

Rule (MOD-D49): a field where people type text accepts any Unicode text, sent as UTF-8. A field
limits its characters only when its value is an identifier that goes in URLs or keys, and then
its error message lists the allowed characters.

## Status

| Item | State |
| --- | --- |
| 1. Audit | done, table below |
| 2. MOD-I233 tenant names | done, names and fnames |
| 3. Other text fields | committed, with MOD-I270 to MOD-I277 below; MOD-I278 is open |
| 4. Storage | already covered by the round trip's UNICODE variant on six vendors; Oracle uses CHAR semantics; limits listed below |
| 5. Portlet search with accented terms | waits for MOD-I142 (persistence-fixes branch) to reach `ccr-43ba0785-hzlrj5` |
| 6. Passwords | committed: UTF-8 on every step of sign-in, UPGRADING text |

## Audit

Search: regexes (`Pattern`, `matches`, `@Pattern`), `\w`, `[a-zA-Z]` and similar classes,
`Character.is*`, `StringUtils.isAlpha*`, length checks (`length()`, `@Size`, `maxlength`), byte
counts, `getBytes()`, `new String(byte[])`, readers and writers without a charset, `URLEncoder` and
`URLDecoder`, and case changes without a `Locale`, over all main Java, JSP, JavaScript, XML and
XSD files. No JSR-303 annotations, `Character.isLetter*` or byte-length checks exist. Java's `\w`
and `\s` are ASCII-only here, because no pattern uses `UNICODE_CHARACTER_CLASS`.

"Text" is a value people read. "ID" is an identifier that goes in URLs, keys or file names.

### Checks that limit characters

| Field | Where | Check | Kind | Result |
| --- | --- | --- | --- | --- |
| Tenant name | `TenantService` | `^[\w ]{5,32}$` | text | **Fixed** (MOD-I233): letters, marks, digits and spaces in any script, 5 to 32 code points |
| Tenant fname | `TenantService` | `^[a-z_0-9]{5,32}$` | ID | kept; the message lists the characters |
| Tenant fname from name | `TenantManagerController.calculateFnameFromName` | `replaceAll("[\\s']", "_").toLowerCase()` | ID | **Fixed**: lower case, ł ø æ œ ß đ ħ þ folded, accents dropped (NFD), other runs become `_`. The add-tenant form has an optional fname field, and asks for one when the name gives none |
| PAGS group name | `PagsService`, `selectEntities.jsp` | `^[\w ]{5,500}$` | text, and the key in the PAGS REST paths | **Fixed**: any text but control characters, `/` and `\`, 5 to 500 code points, in Java and JavaScript |
| PAGS description | `PagsService` | `^[\w ,\.\(\)]{0,500}$` | text | **Fixed**: any text without control characters, up to 500 code points |
| Ad hoc group description | `selectEntities.jsp` builds it from the group names | the description rule | text | **Fixed** with the description rule |
| Portlet list name | `InputValidator`, `PortletList`, `PortletListRESTController` | `^[\w\-]{1,500}$` | text | **Fixed**: any text without control characters, 1 to 255 code points |
| Portlet list id, item fname | `InputValidator` | `^[\w\-]{1,500}$` | ID | kept; the message lists the characters |
| Portlet list owner | `InputValidator` | `^[\w\-]{1,500}$` | user name | **Fixed** (MOD-I270): any user name without spaces or control characters, 1 to 100 code points |
| Portlet fname (every `fname` column) | `FunctionalNameType` | `^[\w-]+$` | ID | kept |
| Portlet fname in imports | `common-types-4.0.xsd` | `[\w\-_]+` (XML Schema `\w` is Unicode) | ID | **Fixed** (MOD-I271): `[A-Za-z0-9_\-]+` |
| Portlet fname in URLs | `UrlSyntaxProviderImpl` | `/p/([a-zA-Z0-9_]+)`, `/f/(...)` | ID | **Fixed** (MOD-I271): `FunctionalNameType.FNAME_REGEX`, which takes `-` |
| Tab external id | `SingleTabUrlNodeSyntaxHelper.makeValid` | non-`[\w-]` becomes `_` | ID | kept |
| Analytics event type | `InputValidator` (core), `PortalEventFactoryImpl` | `^[\w\-]{1,500}$` | ID | **Fixed** (MOD-I272): 1 to 200, the column's size |
| Message key | `MessagesRESTController` | `[A-Za-z0-9_.-]{1,200}` | ID | kept |
| Layout node id | `NodeReferenceFactory`, `JpaAggregatedTabLookupDao` | `[a-zA-Z]\d*`, `u(\d+)l\d+([ns]\d+)` | ID | kept |
| Web component tag, attribute and module names | `WebComponentConfig` | `[a-z][a-z0-9._]*-...` | ID | kept |
| User name (tenant admin contact) | `RDBMUserIdentityStore.validateUsername` | `^[^\s]{1,100}$` | ID | kept |
| Email (tenant admin contact) | `ResetPasswordTenantOperationsListener` | `InternetAddress.validate()` | ID | **Fixed** (MOD-I273): `local@domain.tld` in any script; sending is MOD-I278 |
| Principal key in the portlet editor | `JsonEntityBean.getTypeAndIdHash` | `replaceAll("\\W", "__")` | ID (form field name) | **Fixed** (MOD-I274): characters outside A-Z, a-z and 0-9 become `_<hex>_` |
| Export file names | `SequenceGeneratorPhrase` | `[^a-zA-Z0-9_.-]` becomes `_` | ID (file name) | kept |

### Text without a character check

Local account user name and password (`PersonFormValidator`, `AccountPasswordFormValidator`,
`UserAccountHelper`), local group names (`GroupFormValidator`), portlet titles, names and
descriptions (`PortletDefinitionFormValidator`), tab names (`UpdatePreferencesServlet`), fragment
names, marketplace reviews and search terms check only for blank values, uniqueness or length.
They take any text already. MOD-I272 checks their lengths against the columns.

### Character sets

| Where | Call | Data | Result |
| --- | --- | --- | --- |
| `ChainingSecurityContext.setCredentials(String)` | `getBytes()` | sign-in password, decoded as UTF-8 by `SimpleSecurityContext` | **Fixed**: UTF-8 |
| `SimpleLdapSecurityContext` | `new String(bytes)` | LDAP password | **Fixed**: UTF-8 |
| `JAASSecurityContext` | `new String(bytes)` | JAAS password | **Fixed**: UTF-8 |
| `PortalPasswordServiceImpl` (Jasypt 1.9.3) | `StandardStringDigester` | password hash | UTF-8 already (`MESSAGE_CHARSET`) |
| `CliPasswordUpdateTool` | `InputStreamReader(System.in, UTF_8)` | new password | UTF-8 already; UPGRADING says to use a UTF-8 terminal |
| `EmailPasswordResetNotificationImpl` | `new MimeMessageHelper(message, true)` | reset email | **Fixed** (MOD-I275): UTF-8 |
| `BasicAuthInterceptor` | `getBytes()` | configured Basic auth credentials | **Fixed** (MOD-I275): UTF-8 |
| `JaxbPortalDataHandlerService` | `new FileWriter(...)` | import and export reports | **Fixed** (MOD-I275): UTF-8 |
| `AwsS3DynamicSkinService`, `AbstractDynamicSkinService`, `RemoteUserSettingFilter` | readers and writers without a charset, and the LESS compiler's default | compiled skin CSS, and a file with a user name for development | **Fixed** (MOD-I275): UTF-8 |
| `build.gradle` | javac without `-encoding` | Java sources | open: JDK 18+ reads them as UTF-8, but a build run with `-Dfile.encoding=COMPAT` turns non-ASCII literals in tests into U+FFFD |

The copy tool reads and writes strings through JDBC without converting them, and hashes rows as
UTF-8 (`RowHasher`). Every `URLEncoder` and `URLDecoder` call passes a charset. The Soffit
connector writes the remote response's bytes through unchanged.

### Case changes

Searches that changed case with the server's default locale now use `Locale.ROOT` (MOD-I277).
These keep the default locale, because on a server with a Turkish or Azeri default a change would
alter stored or configured values:

- `SearchRequestAggregationUtil.normalizeSearchTerm` lowercases the search terms that analytics
  stores.
- `LowercasedRegexTester` (PAGS) lowercases the attribute before matching the configured regex.

## Storage (item 4)

The round trip's UNICODE variant writes `EdgeValues.UNICODE_TEXT` into every string field. That
text has accented Latin letters, Japanese, Hebrew, Arabic, Greek, a combining accent and four-byte
emoji, among them a ZWJ sequence. The brief's fields are among them:

- person attribute values, `UP_PERSON_ATTR_VALUES`
- PAGS group names, `UP_PAGS_GROUP.NAME`
- portlet titles, `UP_PORTLET_DEF.TITLE`
- tenant names, `UP_TENANT.TENANT_NAME`

The DB integration workflow runs the round trip on all six vendors:

| Vendor | Stores the text | Why |
| --- | --- | --- |
| HSQLDB 2.7.4 | exactly | Java strings |
| PostgreSQL 18 | exactly | UTF8 database (the image's default) |
| MySQL 9.7 | exactly | `utf8mb4` is the server default, and uPortal's DDL doesn't specify a charset |
| MariaDB 12.3 | exactly | as MySQL |
| Oracle Free 23 | exactly, but `''` is NULL | AL32UTF8, `varchar2(n char)` (see below) |
| SQL Server 2025 | characters outside windows-1252 become `?` | `varchar` columns (MOD-D44); the golden reads the conversion from the server (`DatabaseVendor.sqlServerVarchar`) |

Oracle: uPortal's dialect (`Oracle12ForceClobDialect`) declares every `varchar2` with CHAR
semantics, for entity tables and `tables.xml` tables alike. The recorded DDL
(`hibernate/ddl/oracle-23.sql`) has no `byte` column. The schema step keeps the semantics as
they are.

HSQLDB counts a `VARCHAR` length in UTF-16 units, not code points. A 35-character column holds 17
emoji there, and the code point checks below allow 35. HSQLDB is for development only.

Known 6.0 limits: the schema step list in the persistence-fixes note holds them all, so there is
one list. From this audit it has SQL Server `nvarchar` text (MOD-D44), `utf8mb4` in the MySQL and
MariaDB DDL, Oracle's 4000-byte `varchar2` limit under `MAX_STRING_SIZE=STANDARD` for the columns
over 1000 characters (EXTENDED or `clob` is an owner decision), and
`UP_PROFILE_SELECTION.USERNAME` widened from 70 to 100 characters.

## LOG rows MOD-I270 to MOD-I278

The commits are on `ccr-43ba0785-hzlrj5-utf8-inputs`. Each fix has a test that failed first.

| ID | Area | Issue | Resolution |
| --- | --- | --- | --- |
| MOD-I270 | Portlets | The portlet list owner had to match `^[\w\-]{1,500}$`, so a user name with `.` or `@` (common with CAS) couldn't own a list. | **Fixed** in `074bebc`: owners follow the user name rule (1 to 100 code points without spaces or control characters). |
| MOD-I271 | Portlets | The fname rule differed by layer: the XSD took Unicode letters, `FunctionalNameType` took ASCII, and the URL parser took `[a-zA-Z0-9_]` without `-`. | **Fixed** in `59ad1b9`: one rule, `[A-Za-z0-9_-]+`, in all three. It is wider than the URL parser's and accepts every existing fname. |
| MOD-I272 | Persistence | Some checks allowed more than their columns hold, and some text had no length check. | **Fixed**, counting code points: event types 200 (`1cb565c`), portlet fname, title, name and description (`408b2ac`), tab names 35 (`1369b6f`), local group names and descriptions 255 (`06ae90c`), portlet list names 255 (`8b3ee48`). Open for the schema step: `UP_PROFILE_SELECTION.USERNAME` 70 against user names of 100. |
| MOD-I273 | Tenants | The tenant admin contact email was checked with strict RFC 822, which refuses non-ASCII local parts. | **Fixed** in `3274aea` and `7d6ae59`: an address is `local@domain.tld` in any script, up to 254 code points. 7d6ae59 checks it part by part. |
| MOD-I274 | Portlets | `JsonEntityBean.getTypeAndIdHash` turned every character outside ASCII letters, digits and `_` into `__`, so principals "pags.Café" and "pags.Cafè" shared one permission checkbox name in the portlet editor, and a permission ticked for one was saved for both. | **Fixed** in `1f2e24a`: other characters, `_` included, become `_<hex code point>_`. |
| MOD-I275 | Other | Some readers and writers used the platform charset. Among them were the password reset email, the import and export reports and the skin files. | **Fixed** in `6916fbf`: UTF-8 in each. |
| MOD-I276 | Marketplace | `MarketplaceRatingImpl` cut a review at the length before trimming, so a review with spaces around it could throw, and the cut could split a surrogate pair. | **Fixed** in `187c4c5`: trim first, cut at 160 code points. |
| MOD-I277 | Search | The portlet browser built `new RegExp(term, 'i')` from the search box, so `(` or `C++` threw. Searches changed case with the default locale, so under `tr_TR` "info" didn't find "INFO". | **Fixed** in `5fe0835`: the term is escaped (with the `u` flag), and searches use `Locale.ROOT`. SmartLDAP already escaped its term. It now uses `Locale.ROOT` too. |
| MOD-I278 | Tenants | An address with a Unicode local part or domain passes MOD-I273, but JavaMail sends it only with `mail.mime.allowutf8=true` and an SMTPUTF8 server, and a Unicode domain needs IDNA conversion. | Open: decide whether 6.0 sets `mail.mime.allowutf8` and converts domains, or documents the limit. |
