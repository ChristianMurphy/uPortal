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
| 3. Other text fields | PAGS group names and descriptions, portlet list names: committed |
| 4. Storage | already covered by the round trip's UNICODE variant on six vendors; limits listed below |
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
| Portlet list id, owner, item fname | `InputValidator` | `^[\w\-]{1,500}$` | ID | kept; the message lists the characters (see MOD-I270 for the owner) |
| Portlet fname (every `fname` column) | `FunctionalNameType` | `^[\w-]+$` | ID | kept |
| Portlet fname in imports | `common-types-4.0.xsd` | `[\w\-_]+` (XML Schema `\w` is Unicode) | ID | MOD-I271 |
| Portlet fname in URLs | `UrlSyntaxProviderImpl` | `/p/([a-zA-Z0-9_]+)`, `/f/(...)` | ID | MOD-I271 |
| Tab external id | `SingleTabUrlNodeSyntaxHelper.makeValid` | non-`[\w-]` becomes `_` | ID | kept |
| Analytics event type | `InputValidator` (core), `PortalEventFactoryImpl` | `^[\w\-]{1,500}$` | ID | kept (column 200, see MOD-I272) |
| Message key | `MessagesRESTController` | `[A-Za-z0-9_.-]{1,200}` | ID | kept |
| Layout node id | `NodeReferenceFactory`, `JpaAggregatedTabLookupDao` | `[a-zA-Z]\d*`, `u(\d+)l\d+([ns]\d+)` | ID | kept |
| Web component tag, attribute and module names | `WebComponentConfig` | `[a-z][a-z0-9._]*-...` | ID | kept |
| User name (tenant admin contact) | `RDBMUserIdentityStore.validateUsername` | `^[^\s]{1,100}$` | ID | kept |
| Email (tenant admin contact) | `ResetPasswordTenantOperationsListener` | `InternetAddress.validate()` | ID | kept; non-ASCII local parts are refused (MOD-I273) |
| Entity id in the entity selector | `JsonEntityBean` | `replaceAll("\\W", "__")` | ID (DOM key) | MOD-I274 |
| Export file names | `SequenceGeneratorPhrase` | `[^a-zA-Z0-9_.-]` becomes `_` | ID (file name) | kept |

### Text without a character check

Local account user name and password (`PersonFormValidator`, `AccountPasswordFormValidator`,
`UserAccountHelper`), local group names (`GroupFormValidator`), portlet titles, names and
descriptions (`PortletDefinitionFormValidator`), tab names (`UpdatePreferencesServlet`), fragment
names, marketplace reviews and search terms check only for blank values, uniqueness or length.
They take any text already. Their lengths are listed under MOD-I272.

### Character sets

| Where | Call | Data | Result |
| --- | --- | --- | --- |
| `ChainingSecurityContext.setCredentials(String)` | `getBytes()` | sign-in password, decoded as UTF-8 by `SimpleSecurityContext` | **Fixed**: UTF-8 |
| `SimpleLdapSecurityContext` | `new String(bytes)` | LDAP password | **Fixed**: UTF-8 |
| `JAASSecurityContext` | `new String(bytes)` | JAAS password | **Fixed**: UTF-8 |
| `PortalPasswordServiceImpl` (Jasypt 1.9.3) | `StandardStringDigester` | password hash | UTF-8 already (`MESSAGE_CHARSET`) |
| `CliPasswordUpdateTool` | `InputStreamReader(System.in, UTF_8)` | new password | UTF-8 already; UPGRADING says to use a UTF-8 terminal |
| `EmailPasswordResetNotificationImpl` | `new MimeMessageHelper(message, true)` | reset email | MOD-I275 |
| `BasicAuthInterceptor` | `getBytes()` | configured Basic auth credentials | MOD-I275 |
| `JaxbPortalDataHandlerService` | `new FileWriter(...)` | import and export reports | MOD-I275 |
| `AwsS3DynamicSkinService`, `AbstractDynamicSkinService`, `RemoteUserSettingFilter` | readers and writers without a charset | compiled skin CSS, and a file with a user name for development | MOD-I275 |

The copy tool reads and writes strings through JDBC without converting them, and hashes rows as
UTF-8 (`RowHasher`). Every `URLEncoder` and `URLDecoder` call passes a charset.

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
| Oracle Free 23 | exactly, but `''` is NULL | AL32UTF8, `varchar2(n char)` |
| SQL Server 2025 | characters outside windows-1252 become `?` | `varchar` columns (MOD-D44); the golden reads the conversion from the server (`DatabaseVendor.sqlServerVarchar`) |

Known 6.0 limits, for the 6.0 schema step (also in the persistence-fixes note's list):

- SQL Server: `nvarchar` text (MOD-D44).
- MySQL and MariaDB: the 6.0 DDL should name `utf8mb4` for its tables, so a server configured
  with another default (`utf8mb3` or `latin1`) can't create tables that lose four-byte
  characters.

## Proposed new LOG rows

| ID | Area | Issue | Proposed resolution |
| --- | --- | --- | --- |
| MOD-I270 | Portlets | The portlet list owner must match `^[\w\-]{1,500}$`, so a user name with `.` or `@` (common with CAS) can't own a list. | Check owners with the user name rule (`^[^\s]{1,100}$`). |
| MOD-I271 | Portlets | The fname rule differs by layer: XSD `[\w\-_]+` takes Unicode letters, `FunctionalNameType` takes ASCII, and the URL parser takes `[a-zA-Z0-9_]` without `-`. An import can pass the schema and then fail, and a fname with `-` doesn't parse from a URL. | Use one rule (`[A-Za-z0-9_-]+`) in all three. |
| MOD-I272 | Persistence | Some checks allow more than their columns hold. The event type check allows 500 for `EVENT_TYPE` 200, and the user name check 100 for `ProfileSelection.USERNAME` 70. The portlet list name allowed 500 for 255 (fixed here). Tab names have no check against `UP_LAYOUT_STRUCT.NAME` 35. | Align the checks with the columns, or widen the columns in the 6.0 schema step. |
| MOD-I273 | Tenants | The tenant admin contact email is checked with strict RFC 822, which refuses internationalized addresses (non-ASCII local parts). | Decide whether 6.0 accepts SMTPUTF8 addresses. |
| MOD-I274 | Groups | `JsonEntityBean` turns every non-ASCII character of an entity id into `__` for DOM keys, so two ids can collide. | Encode ids instead of replacing characters. |
| MOD-I275 | Other | Some readers and writers use the platform charset. Among them are the password reset email (`MimeMessageHelper` without an encoding), the configured Basic auth credentials and the import and export reports. The skin service files and a user name filter for development do too. | Pass UTF-8 explicitly. |
| MOD-I276 | Marketplace | `MarketplaceRatingImpl` cuts a review with `review.trim().substring(0, Math.min(review.length(), 160))`, using the length before trimming. A review with spaces around it can throw `StringIndexOutOfBoundsException`, and the cut can split a surrogate pair. | Trim first, cut at a code point boundary. |
| MOD-I277 | Search | The portlet browser builds `new RegExp(query, 'i')` from the search term, so `(` or `[` throws. SmartLDAP group search uses the term as a regex as well. Several searches change case without a `Locale`. | Escape the term, and use `Locale.ROOT`. |
