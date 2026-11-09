#!/usr/bin/env python3
"""Checks a running local portal over HTTP and writes what it saw.

    portal_check.py <label> <out directory> <Tomcat logs directory> [section ...]

Sections, all by default:
  smoke  sign in, load the user's pages, sign out, as student and as admin
  head   the stylesheet and script links in the page head, and whether each one loads
  load   a minute of parallel page loads, and how many nodeList2Iterator lines the logs gained
  race   a page request sent within a few milliseconds of /Logout, many times: does a session
         that the page request started keep showing the logged-out user?

A section that throws is recorded as an error, and the others still run.
"""
import collections
import html
import http.client
import json
import os
import random
import re
import statistics
import sys
import threading
import time
import traceback

HOST, PORT = 'localhost', 8080
USERS = {
    'student': 'Steven Student',
    'admin': 'Amy Administrator',
    'faculty': 'Felicia Faculty',
    'staff': 'Samuel Staff',
}
WELCOME = '/uPortal/f/welcome/normal/render.uP'


class Client:
    """One keep-alive connection and a cookie jar for the /uPortal path."""

    def __init__(self, cookies=None):
        self.conn = http.client.HTTPConnection(HOST, PORT, timeout=120)
        self.cookies = dict(cookies or {})

    def connect(self):
        self.conn.connect()

    def get(self, path, follow=True):
        """GET path. Returns (status, body, final path, Set-Cookie headers of the last answer)."""
        for _ in range(10):
            headers = {}
            if self.cookies and path.startswith('/uPortal'):
                headers['Cookie'] = '; '.join(f'{k}={v}' for k, v in self.cookies.items())
            try:
                self.conn.request('GET', path, headers=headers)
                resp = self.conn.getresponse()
            except (http.client.HTTPException, OSError):
                self.conn.close()
                self.conn = http.client.HTTPConnection(HOST, PORT, timeout=120)
                self.conn.request('GET', path, headers=headers)
                resp = self.conn.getresponse()
            body = resp.read().decode('utf-8', 'replace')
            set_cookies = resp.headers.get_all('Set-Cookie') or []
            for sc in set_cookies:
                name, _, value = sc.split(';', 1)[0].partition('=')
                if re.search(r'path=/uPortal', sc, re.I):
                    self.cookies[name.strip()] = value.strip()
            location = resp.headers.get('Location')
            if follow and resp.status in (301, 302, 303, 307) and location:
                path = re.sub(r'^https?://[^/]+', '', location)
                continue
            return resp.status, body, path, set_cookies
        raise RuntimeError('too many redirects from ' + path)

    def close(self):
        self.conn.close()


def login(client, user):
    return client.get(f'/uPortal/Login?userName={user}&password={user}')


def render_links(body):
    """The page's links to other portal pages, in order, without duplicates."""
    links = []
    for href in re.findall(r'href="(/uPortal/[^"]*render\.uP[^"]*)"', body):
        href = html.unescape(href)
        if href not in links and 'Log' not in href:
            links.append(href)
    return links


def session_user(client):
    """What the REST API says about the session's user."""
    status, body, _, _ = client.get('/uPortal/api/session.json')
    try:
        return json.loads(body).get('person', {}).get('userName')
    except ValueError:
        return f'HTTP {status}'


def count_in_logs(logs, needle):
    count = 0
    for root, _, files in os.walk(logs):
        for name in files:
            try:
                with open(os.path.join(root, name), errors='replace') as log:
                    count += sum(1 for line in log if needle in line)
            except OSError:
                pass
    return count


def smoke(_logs):
    out = {}
    for user in ('student', 'admin'):
        name = USERS[user]
        client = Client()
        seen = {}
        status, body, path, _ = client.get('/uPortal/')
        seen['guest page'] = {'status': status, 'path': path, 'shows user': name in body}
        status, body, path, _ = login(client, user)
        seen['after sign-in'] = {'status': status, 'path': path, 'shows user': name in body}
        seen['REST user after sign-in'] = session_user(client)
        pages = render_links(body)[:8]
        loaded = []
        for page in pages:
            status, page_body, _, _ = client.get(page)
            loaded.append({'path': page, 'status': status, 'shows user': name in page_body})
        seen['pages'] = loaded
        status, body, path, _ = client.get('/uPortal/Logout')
        seen['sign-out'] = {
            'status': status,
            'path': path,
            'says "Logout successful"': 'Logout successful' in body,
        }
        status, body, path, _ = client.get('/uPortal/')
        seen['page after sign-out'] = {'status': status, 'path': path, 'shows user': name in body}
        seen['REST user after sign-out'] = session_user(client)
        client.close()
        seen['ok'] = (
            seen['after sign-in']['status'] == 200
            and seen['after sign-in']['shows user']
            and bool(loaded)
            and all(p['status'] == 200 and p['shows user'] for p in loaded)
            and seen['page after sign-out']['status'] == 200
            and not seen['page after sign-out']['shows user']
        )
        out[user] = seen
    out['ok'] = all(out[user]['ok'] for user in ('student', 'admin'))
    return out


def head(_logs):
    out = {}
    for who in ('guest', 'student'):
        client = Client()
        if who == 'guest':
            status, body, _, _ = client.get('/uPortal/')
        else:
            status, body, _, _ = login(client, who)
        end = body.find('</head>')
        page_head = body[:end] if end > 0 else ''
        urls = []
        for tag in re.findall(r'<link\b[^>]*>', page_head):
            href = re.search(r'href="([^"]+)"', tag)
            if href and re.search(r'rel="stylesheet"', tag):
                urls.append(('css', html.unescape(href.group(1))))
        for src in re.findall(r'<script\b[^>]*\bsrc="([^"]+)"', page_head):
            urls.append(('js', html.unescape(src)))
        fetched = []
        for kind, url in urls:
            if url.startswith('/'):
                res_status, res_body, _, _ = client.get(url)
                fetched.append({'kind': kind, 'url': url, 'status': res_status, 'bytes': len(res_body)})
            else:
                fetched.append({'kind': kind, 'url': url, 'status': 'not fetched (other host)'})
        client.close()
        local = [f for f in fetched if isinstance(f['status'], int)]
        out[who] = {
            'page status': status,
            'head found': end > 0,
            'css links': sum(1 for f in fetched if f['kind'] == 'css'),
            'js links': sum(1 for f in fetched if f['kind'] == 'js'),
            'aggregated links': sum(1 for f in fetched if 'aggr' in f['url']),
            'local links that did not load': [f for f in local if f['status'] != 200 or not f['bytes']],
            'links': fetched,
        }
    out['ok'] = all(
        out[who]['head found']
        and out[who]['css links'] > 0
        and out[who]['js links'] > 0
        and not out[who]['local links that did not load']
        for who in ('guest', 'student')
    )
    return out


def load(logs, seconds=60, workers=16):
    before = count_in_logs(logs, 'nodeList2Iterator')
    statuses = collections.Counter()
    errors = collections.Counter()
    failed = []
    lock = threading.Lock()
    end = time.time() + seconds

    def note(status, path):
        with lock:
            statuses[status] += 1
            if status >= 500 and len(failed) < 10:
                failed.append(path)

    def work(seed):
        rng = random.Random(seed)
        while time.time() < end:
            user = rng.choice([None, 'student', 'admin', 'faculty', 'staff'])
            client = Client()
            try:
                if user:
                    status, body, path, _ = login(client, user)
                else:
                    status, body, path, _ = client.get('/uPortal/')
                note(status, path)
                pages = render_links(body)[:12]
                rng.shuffle(pages)
                for page in pages:
                    if time.time() >= end:
                        break
                    note(client.get(page)[0], page)
                if user:
                    client.get('/uPortal/Logout', follow=False)
            except Exception as e:  # noqa: BLE001 - a failed request is a result here
                with lock:
                    errors[type(e).__name__] += 1
            finally:
                client.close()

    threads = [threading.Thread(target=work, args=(i,)) for i in range(workers)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    time.sleep(3)
    after = count_in_logs(logs, 'nodeList2Iterator')
    return {
        'seconds': seconds,
        'workers': workers,
        'requests by status': {str(k): v for k, v in sorted(statuses.items())},
        'request errors': dict(errors),
        'first paths that answered 5xx': failed,
        'nodeList2Iterator log lines before': before,
        'nodeList2Iterator log lines gained': after - before,
    }


def race(_logs, attempts=300, probes=4):
    user, name = 'student', USERS['student']

    def new_session_id(set_cookies, old):
        for sc in set_cookies:
            m = re.match(r'\s*JSESSIONID=([^;]+)', sc)
            if m and re.search(r'path=/uPortal', sc, re.I) and m.group(1) != old:
                return m.group(1)
        return None

    # How long /Logout takes to answer, to know which delays to try
    latencies = []
    for _ in range(5):
        client = Client()
        login(client, user)
        start = time.time()
        client.get('/uPortal/Logout', follow=False)
        latencies.append(time.time() - start)
        client.close()
    logout_time = statistics.median(latencies)

    counts = collections.Counter()
    stale = []
    for attempt in range(attempts):
        client = Client()
        status, body, path, _ = login(client, user)
        if status != 200 or name not in body:
            counts['sign-in failed'] += 1
            client.close()
            continue
        old = dict(client.cookies)
        client.close()
        # Probes start from 3 ms before the logout is sent to a little after it answers
        span = logout_time * 1.3 + 0.003
        offset = -0.003 + (attempt % 30) / 29 * span
        results = [None] * probes
        lead = 0.01
        start_at = time.time() + lead

        def probe(k, offset=offset, start_at=start_at, old=old, results=results):
            c = Client(old)
            try:
                c.connect()
                delay = start_at + max(0.0, offset + k * 0.0015 + 0.003) - time.time()
                if delay > 0:
                    time.sleep(delay)
                results[k] = c.get(WELCOME, follow=False)
            except Exception as e:  # noqa: BLE001
                results[k] = e
            finally:
                c.close()

        def logout(offset=offset, start_at=start_at, old=old):
            c = Client(old)
            try:
                c.connect()
                delay = start_at + 0.003 - time.time()
                if delay > 0:
                    time.sleep(delay)
                c.get('/uPortal/Logout', follow=False)
            except Exception:  # noqa: BLE001
                pass
            finally:
                c.close()

        threads = [threading.Thread(target=probe, args=(k,)) for k in range(probes)]
        threads.append(threading.Thread(target=logout))
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        for k, result in enumerate(results):
            if not isinstance(result, tuple):
                counts['probe failed'] += 1
                continue
            p_status, p_body, _, p_cookies = result
            sid = new_session_id(p_cookies, old.get('JSESSIONID'))
            if sid is None:
                counts['probe kept the old session' + (', page of the user' if name in p_body else '')] += 1
                continue
            check = Client({'JSESSIONID': sid})
            c_status, c_body, c_path, _ = check.get(WELCOME)
            rest_user = session_user(check)
            check.close()
            if name in c_body:
                counts['NEW SESSION SHOWS THE LOGGED-OUT USER'] += 1
                if len(stale) < 10:
                    stale.append({
                        'attempt': attempt,
                        'probe': k,
                        'probe sent after logout (ms)': round((offset + k * 0.0015) * 1000, 1),
                        'probe status': p_status,
                        'next page status': c_status,
                        'next page path': c_path,
                        'next page shows': name,
                        'REST user of that session': rest_user,
                    })
            else:
                counts['probe got a new session, which shows the guest page'] += 1
    return {
        'attempts': attempts,
        'page requests per attempt': probes,
        'median time of /Logout (ms)': round(logout_time * 1000, 1),
        'outcomes': dict(counts),
        'sessions that showed the logged-out user': counts['NEW SESSION SHOWS THE LOGGED-OUT USER'],
        'first such sessions': stale,
    }


SECTIONS = {'smoke': smoke, 'head': head, 'load': load, 'race': race}


def main():
    label, out_dir, logs = sys.argv[1:4]
    wanted = sys.argv[4:] or list(SECTIONS)
    results = {}
    for name in wanted:
        started = time.time()
        try:
            results[name] = SECTIONS[name](logs)
        except Exception:  # noqa: BLE001
            results[name] = {'error': traceback.format_exc()}
        results[name]['took seconds'] = round(time.time() - started, 1)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, f'portal-check-{label}.json'), 'w') as out:
        json.dump(results, out, indent=2)
    brief = json.loads(json.dumps(results))
    for who in ('guest', 'student'):
        brief.get('head', {}).get(who, {}).pop('links', None)
    print(f'=== portal check: {label}')
    print(json.dumps(brief, indent=2))


if __name__ == '__main__':
    main()
