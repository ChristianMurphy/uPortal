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
package org.apereo.portal.dbmigrate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Everything a run is configured with; filled by {@link DbMigrateCli}. */
public final class MigrationOptions {

    public enum Mode {
        /** Copy, position generators, verify. */
        COPY,
        /** Plan and row counts only; nothing is written. */
        DRY_RUN,
        /** Compare an already copied target with the source. */
        VERIFY_ONLY
    }

    /** What to do with PostgreSQL large-object references whose object no longer exists. */
    public enum MissingLobPolicy {
        FAIL,
        SKIP_ROW
    }

    /** The table {@code --skip-raw-events} leaves out (portal event history, MOD-D11). */
    public static final String RAW_EVENTS_TABLE = "UP_RAW_EVENTS";

    private ConnectionSpec source;
    private ConnectionSpec target;
    private final List<String> includes = new ArrayList<>();
    private final List<String> excludes = new ArrayList<>();
    private boolean skipRawEvents;
    private int batchSize = 1000;
    private int fetchSize;
    private int threads = 4;
    private Mode mode = Mode.COPY;
    private boolean clearTarget;
    private boolean verify = true;
    private String sourceProfile = "5.17";
    private String targetProfile = "5.17";
    private final List<Path> ruleFiles = new ArrayList<>();
    private MissingLobPolicy missingLobPolicy = MissingLobPolicy.FAIL;
    private Path report;

    public ConnectionSpec source() {
        return source;
    }

    public MigrationOptions source(ConnectionSpec spec) {
        this.source = spec;
        return this;
    }

    public ConnectionSpec target() {
        return target;
    }

    public MigrationOptions target(ConnectionSpec spec) {
        this.target = spec;
        return this;
    }

    public List<String> includes() {
        return includes;
    }

    public List<String> excludes() {
        return excludes;
    }

    public boolean skipRawEvents() {
        return skipRawEvents;
    }

    public MigrationOptions skipRawEvents(boolean v) {
        this.skipRawEvents = v;
        return this;
    }

    public int batchSize() {
        return batchSize;
    }

    public MigrationOptions batchSize(int v) {
        if (v < 1) {
            throw new IllegalArgumentException("--batch-size must be at least 1");
        }
        this.batchSize = v;
        return this;
    }

    /** Rows per network round trip when reading; defaults to the batch size. */
    public int fetchSize() {
        return fetchSize > 0 ? fetchSize : batchSize;
    }

    public MigrationOptions fetchSize(int v) {
        this.fetchSize = v;
        return this;
    }

    public int threads() {
        return threads;
    }

    public MigrationOptions threads(int v) {
        if (v < 1) {
            throw new IllegalArgumentException("--threads must be at least 1");
        }
        this.threads = v;
        return this;
    }

    public Mode mode() {
        return mode;
    }

    public MigrationOptions mode(Mode v) {
        this.mode = v;
        return this;
    }

    public boolean clearTarget() {
        return clearTarget;
    }

    public MigrationOptions clearTarget(boolean v) {
        this.clearTarget = v;
        return this;
    }

    public boolean verify() {
        return verify;
    }

    public MigrationOptions verify(boolean v) {
        this.verify = v;
        return this;
    }

    public String sourceProfile() {
        return sourceProfile;
    }

    public MigrationOptions sourceProfile(String v) {
        this.sourceProfile = v;
        return this;
    }

    public String targetProfile() {
        return targetProfile;
    }

    public MigrationOptions targetProfile(String v) {
        this.targetProfile = v;
        return this;
    }

    public List<Path> ruleFiles() {
        return ruleFiles;
    }

    public MissingLobPolicy missingLobPolicy() {
        return missingLobPolicy;
    }

    public MigrationOptions missingLobPolicy(MissingLobPolicy v) {
        this.missingLobPolicy = v;
        return this;
    }

    public Path report() {
        return report;
    }

    public MigrationOptions report(Path v) {
        this.report = v;
        return this;
    }
}
