/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.dbmigrate.plan;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The characters a text column can hold, when that is less than Unicode (MOD-I202): a code page
 * (SQL Server {@code varchar} on a collation that isn't UTF-8, a MySQL/MariaDB {@code latin1}
 * column, a PostgreSQL or Oracle database in a single-byte character set), or Unicode without the
 * supplementary characters (MySQL/MariaDB {@code utf8mb3}, which can't hold emoji).
 *
 * <p>The {@code for...} methods map a database's own name for its character set to one of these, or
 * return {@link #UNICODE} for a character set that holds every character, or null for one this
 * class doesn't know.
 */
public final class TextEncoding {

    /** Holds every character. */
    public static final TextEncoding UNICODE = new TextEncoding("Unicode", null, false);

    /** Unicode without the supplementary characters: emoji, and some CJK, historic scripts. */
    public static final TextEncoding BMP_ONLY =
            new TextEncoding("Unicode without supplementary characters", null, true);

    private final String name;
    private final Charset charset;
    private final boolean bmpOnly;

    private TextEncoding(String name, Charset charset, boolean bmpOnly) {
        this.name = name;
        this.charset = charset;
        this.bmpOnly = bmpOnly;
    }

    /** The code page of a Java character set, e.g. {@code windows-1252}. */
    public static TextEncoding of(Charset charset) {
        return new TextEncoding(charset.name(), charset, false);
    }

    public String name() {
        return name;
    }

    /** Whether every character fits, so that no value needs checking. */
    public boolean isUnicode() {
        return charset == null && !bmpOnly;
    }

    /**
     * The first code point of {@code value} this encoding can't hold, or -1 if it holds them all.
     */
    public int firstUnencodable(String value) {
        if (isUnicode()) {
            return -1;
        }
        if (bmpOnly) {
            return value.codePoints()
                    .filter(Character::isSupplementaryCodePoint)
                    .findFirst()
                    .orElse(-1);
        }
        CharsetEncoder encoder = charset.newEncoder();
        if (encoder.canEncode(value)) {
            return -1;
        }
        return value.codePoints()
                .filter(cp -> !encoder.canEncode(new String(Character.toChars(cp))))
                .findFirst()
                .orElse(-1);
    }

    @Override
    public String toString() {
        return name;
    }

    // ------------------------------------------------------------------ per database

    /**
     * A SQL Server collation's code page ({@code COLLATIONPROPERTY(name, 'CodePage')}) for a {@code
     * char}, {@code varchar} or {@code text} column: 65001 for a UTF-8 collation, 0 for one with
     * Unicode only, else a Windows code page such as 1252.
     */
    public static TextEncoding forSqlServerCodePage(int codePage) {
        if (codePage == 0 || codePage == 65001) {
            return UNICODE;
        }
        return charset("windows-" + codePage, "x-windows-" + codePage, "Cp" + codePage);
    }

    private static final Map<String, String> MYSQL = new HashMap<>();

    static {
        // MySQL's latin1 is windows-1252, not ISO-8859-1
        MYSQL.put("latin1", "windows-1252");
        MYSQL.put("ascii", "US-ASCII");
        MYSQL.put("latin2", "ISO-8859-2");
        MYSQL.put("latin5", "ISO-8859-9");
        MYSQL.put("latin7", "ISO-8859-13");
        MYSQL.put("greek", "ISO-8859-7");
        MYSQL.put("hebrew", "ISO-8859-8");
        MYSQL.put("cp1250", "windows-1250");
        MYSQL.put("cp1251", "windows-1251");
        MYSQL.put("cp1256", "windows-1256");
        MYSQL.put("cp1257", "windows-1257");
        MYSQL.put("cp850", "IBM850");
        MYSQL.put("cp852", "IBM852");
        MYSQL.put("cp866", "IBM866");
        MYSQL.put("koi8r", "KOI8-R");
        MYSQL.put("koi8u", "KOI8-U");
        MYSQL.put("sjis", "Shift_JIS");
        MYSQL.put("cp932", "windows-31j");
        MYSQL.put("ujis", "EUC-JP");
        MYSQL.put("euckr", "EUC-KR");
        MYSQL.put("big5", "Big5");
        MYSQL.put("gb2312", "GB2312");
        MYSQL.put("gbk", "GBK");
        MYSQL.put("tis620", "TIS-620");
    }

    /** A MySQL/MariaDB column's {@code CHARACTER_SET_NAME}. */
    public static TextEncoding forMySql(String characterSet) {
        String cs = characterSet.toLowerCase(Locale.ROOT);
        switch (cs) {
            case "utf8mb4":
            case "utf16":
            case "utf16le":
            case "utf32":
            case "gb18030":
                return UNICODE;
            case "utf8":
            case "utf8mb3":
            case "ucs2":
                return BMP_ONLY;
            default:
                String java = MYSQL.get(cs);
                return java == null ? null : charset(java);
        }
    }

    /** A PostgreSQL database's encoding ({@code pg_encoding_to_char(encoding)}). */
    public static TextEncoding forPostgres(String encoding) {
        String e = encoding.toUpperCase(Locale.ROOT);
        if (e.equals("UTF8") || e.equals("UNICODE") || e.equals("GB18030")) {
            return UNICODE;
        }
        if (e.startsWith("WIN") && e.length() == 7) {
            return charset("windows-" + e.substring(3));
        }
        switch (e) {
            case "SQL_ASCII":
                // Stores the client's bytes unchecked; nothing to tell from the database
                return UNICODE;
            case "LATIN1":
                return charset("ISO-8859-1");
            case "LATIN2":
                return charset("ISO-8859-2");
            case "LATIN9":
                return charset("ISO-8859-15");
            case "EUC_JP":
                return charset("EUC-JP");
            case "EUC_KR":
                return charset("EUC-KR");
            case "SJIS":
                return charset("Shift_JIS");
            case "BIG5":
                return charset("Big5");
            case "GBK":
                return charset("GBK");
            case "KOI8R":
                return charset("KOI8-R");
            default:
                return null;
        }
    }

    /** An Oracle character set ({@code NLS_CHARACTERSET} or {@code NLS_NCHAR_CHARACTERSET}). */
    public static TextEncoding forOracle(String characterSet) {
        switch (characterSet.toUpperCase(Locale.ROOT)) {
            case "AL32UTF8":
            case "UTF8":
            case "AL16UTF16":
                return UNICODE;
            case "US7ASCII":
                return charset("US-ASCII");
            case "WE8MSWIN1252":
                return charset("windows-1252");
            case "WE8ISO8859P1":
                return charset("ISO-8859-1");
            case "WE8ISO8859P15":
                return charset("ISO-8859-15");
            case "EE8MSWIN1250":
                return charset("windows-1250");
            case "EE8ISO8859P2":
                return charset("ISO-8859-2");
            case "CL8MSWIN1251":
                return charset("windows-1251");
            case "JA16SJIS":
                return charset("Shift_JIS");
            case "ZHS16GBK":
                return charset("GBK");
            case "KO16MSWIN949":
                return charset("x-windows-949");
            default:
                return null;
        }
    }

    /** The first of the Java names this JVM supports, or null. */
    private static TextEncoding charset(String... javaNames) {
        for (String n : javaNames) {
            try {
                if (Charset.isSupported(n)) {
                    return of(Charset.forName(n));
                }
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                // try the next name
            }
        }
        return null;
    }
}
