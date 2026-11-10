#!/usr/bin/env python3
"""Exports the permission sets, deletes two of them, imports the exported files, and says whether
the rows are back.

    perm.py before|after <uPortal-start directory> <out directory> <local Maven repository>

"before" runs with the master build deployed and "after" with the fix deployed, on the same
database. "after" first imports the files that "before" exported, then does its own round.
Needs uPortal-start's HSQLDB server running. Exits 1 if "after" doesn't bring the rows back.
"""
import glob
import json
import os
import subprocess
import sys
import time

phase, start, out, m2 = sys.argv[1:5]
here = os.path.dirname(os.path.abspath(__file__))
NO_IMPORTER = 'has no registered importer or upgrader support'
COLUMNS = 'OWNER, PRINCIPAL_TYPE, PRINCIPAL_KEY, ACTIVITY, TARGET, PERMISSION_TYPE'
os.makedirs(out, exist_ok=True)
report = {}


def hsqldb_jar():
    """The HSQLDB jar of the version uPortal-start runs its server with."""
    with open(os.path.join(start, 'gradle.properties')) as properties:
        version = [line.split('=', 1)[1].strip() for line in properties if line.startswith('hsqldbVersion=')][0]
    return glob.glob(os.path.expanduser(
        f'~/.gradle/caches/modules-2/files-2.1/org.hsqldb/hsqldb/{version}/*/hsqldb-{version}.jar'))[0]


def sql(statement):
    done = subprocess.run(
        ['java', '-cp', hsqldb_jar(), os.path.join(here, 'Sql.java'), statement],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
    return [line for line in done.stdout.splitlines() if line]


def rows():
    return sorted(sql(f'SELECT {COLUMNS} FROM UP_PERMISSION'))


def gradle(name, *args):
    """Runs a uPortal-start task, keeps its log, and returns (exit code, log text)."""
    log_path = os.path.join(out, f'{phase}-{name}.log')
    with open(log_path, 'w') as log:
        code = subprocess.run(
            ['./gradlew', '--no-daemon', f'-Dmaven.repo.local={m2}', *args],
            cwd=start, stdout=log, stderr=subprocess.STDOUT).returncode
    with open(log_path, errors='replace') as log:
        return code, log.read()


def export(directory):
    code, _ = gradle('export-' + os.path.basename(directory),
                     ':overlays:uPortal:dataExport', '-Dtype=permission-set', f'-Ddir={directory}')
    files = glob.glob(os.path.join(directory, '**', '*.xml'), recursive=True)
    scripts = set()
    for path in files:
        with open(path, errors='replace') as exported:
            for line in exported:
                if '<permission-set' in line:
                    scripts.add(line.strip())
    return {'gradle exit code': code, 'files': len(files), 'root elements': sorted(scripts)}


def import_dir(name, directory):
    # "dataImp" is Gradle's short name for the dataImport task. With the full name, uPortal-start
    # master fails before it runs any task: overlays/uPortal/build.gradle leaves out
    # prepareSkinResources when a task name contains "dataImport", and gradle/tasks/playwright.gradle
    # then looks that task up ("Task with path ':overlays:uPortal:prepareSkinResources' not found").
    code, log = gradle('import-' + name, ':overlays:uPortal:dataImp', f'-Ddir={directory}')
    print(f'--- end of the dataImport output ({name}, {phase})')
    print('\n'.join([line[:300] for line in log.splitlines() if line.strip()][-30:]))
    print('---')
    # The import also writes a report per file under the overlay's build directory
    in_reports = 0
    for path in glob.glob(os.path.join(start, 'overlays', 'uPortal', 'build', '**', '*'), recursive=True):
        if os.path.isfile(path) and os.path.getmtime(path) >= started:
            try:
                with open(path, errors='replace') as text:
                    in_reports += text.read().count(NO_IMPORTER)
            except OSError:
                pass
    lines = [line.strip() for line in log.splitlines() if NO_IMPORTER in line]
    return {
        'gradle exit code': code,
        f'lines with "{NO_IMPORTER}" in the task output': len(lines),
        'the same words in report files': in_reports,
        'first such line': lines[0][:400] if lines else None,
    }


def exported_sets(directory):
    """(owner, activity) of two exported files that name all of that owner and activity's rows."""
    import re
    chosen = []
    for path in sorted(glob.glob(os.path.join(directory, '**', '*.xml'), recursive=True)):
        with open(path, errors='replace') as exported:
            text = exported.read()
        owner = re.search(r'<owner>([^<]+)</owner>', text).group(1)
        activity = re.search(r'<activity>([^<]+)</activity>', text).group(1)
        # The portlets' own SUBSCRIBE and BROWSE rows are exported with the portlets, not here
        if owner.startswith('UP_PORTLET'):
            continue
        principals = sql(f"SELECT DISTINCT PRINCIPAL_KEY FROM UP_PERMISSION WHERE OWNER='{owner}'"
                         f" AND ACTIVITY='{activity}'")
        if len(principals) == 1:
            chosen.append([owner, activity, os.path.basename(path)])
        if len(chosen) == 2:
            break
    return chosen


def delete_sets(sets):
    deleted = 0
    for owner, activity, _ in sets:
        answer = sql(f"DELETE FROM UP_PERMISSION WHERE OWNER='{owner}' AND ACTIVITY='{activity}'")
        deleted += int(answer[0].split()[0])
    return deleted


started = time.time()
state_path = os.path.join(out, 'perm-state.json')
if phase == 'before':
    all_rows = rows()
    groups = sql('SELECT OWNER, PRINCIPAL_TYPE, PRINCIPAL_KEY, ACTIVITY, COUNT(*) FROM UP_PERMISSION'
                 ' GROUP BY OWNER, PRINCIPAL_TYPE, PRINCIPAL_KEY, ACTIVITY ORDER BY 1, 2, 3, 4')
    report['rows in UP_PERMISSION after dataInit'] = len(all_rows)
    report['distinct owner, principal and activity in UP_PERMISSION'] = len(groups)
    export_dir = os.path.join(out, 'export-master')
    report['export'] = export(export_dir)
    chosen = exported_sets(export_dir)
    report['sets deleted (owner / activity / exported file)'] = [' / '.join(c) for c in chosen]
    report['rows deleted'] = delete_sets(chosen)
    after_delete = rows()
    report['rows after the delete'] = len(after_delete)
    report['import of the exported files'] = import_dir('master-export', export_dir)
    after_import = rows()
    report['rows after the import'] = len(after_import)
    report['the deleted rows are back'] = after_import == all_rows
    with open(state_path, 'w') as state:
        json.dump({'rows': all_rows, 'chosen': chosen}, state)
    ok = len(chosen) == 2 and report['rows deleted'] > 0
else:
    with open(state_path) as state:
        saved = json.load(state)
    all_rows, chosen = saved['rows'], saved['chosen']
    report['rows at the start (two sets still deleted)'] = len(rows())
    report['import of the files the master build exported'] = import_dir(
        'master-export', os.path.join(out, 'export-master'))
    after_first = rows()
    report['rows after that import'] = len(after_first)
    report['the deleted rows are back'] = after_first == all_rows
    export_dir = os.path.join(out, 'export-fix')
    report['export with the fix'] = export(export_dir)
    report['sets deleted again (owner / activity / exported file)'] = [' / '.join(c) for c in chosen]
    report['rows deleted'] = delete_sets(chosen)
    report['rows after the delete'] = len(rows())
    report['import of the files the fix build exported'] = import_dir('fix-export', export_dir)
    after_second = rows()
    report['rows after that import'] = len(after_second)
    report['the deleted rows are back again'] = after_second == all_rows
    ok = after_first == all_rows and after_second == all_rows and report['rows deleted'] > 0

with open(os.path.join(out, f'perm-{phase}.json'), 'w') as result:
    json.dump(report, result, indent=2)
print(f'=== permission sets: {phase}')
print(json.dumps(report, indent=2))
sys.exit(0 if ok else 1)
