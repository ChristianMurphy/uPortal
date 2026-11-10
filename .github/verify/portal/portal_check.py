#!/usr/bin/env python3
"""Checks a running local portal over HTTP and writes what it saw.

    portal_check.py <label> <out directory> <Tomcat logs directory> [section ...]

Sections, all by default:
  smoke  sign in, load the user's pages, sign out, as student and as admin
  head   the stylesheet and script links in the page head, and whether each one loads
  load   three minutes of parallel page loads, and how many nodeList2Iterator lines the logs gained
  race   page requests sent within a few milliseconds of /Logout, many times: does a session
         that a page request started keep showing the logged-out user?

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
    """One keep-alive connection and a cookie jar, as one browser would have."""

    # Without a User-Agent header the portal answers 500 (RemoteCookieCheckFilter)
    HEADERS = {
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)'
                      ' Chrome/140.0.0.0 Safari/537.36 portal_check.py',
        'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
    }

    def __init__(self, cookies=None):
        self.conn = http.client.HTTPConnection(HOST, PORT, timeout=120)
        # name -> (value, path)
        self.cookies = {name: (value, '/uPortal') for name, value in (cookies or {}).items()}

    def connect(self):
        self.conn.connect()

    def session_id(self):
        return self.cookies.get('JSESSIONID', (None, None))[0]

    def get(self, path, follow=True):
        """GET path. Returns (status, body, final path, Set-Cookie headers of the last answer)."""
        for _ in range(12):
            headers = dict(self.HEADERS)
            sent = [f'{name}={value}' for name, (value, cookie_path) in self.cookies.items()
                    if path.startswith(cookie_path)]
            if sent:
                headers['Cookie'] = '; '.join(sent)
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
                cookie_path = re.search(r';\s*path=([^;]+)', sc, re.I)
                cookie_path = cookie_path.group(1).strip() if cookie_path else '/'
                if re.search(r';\s*max-age=0\b', sc, re.I):
                    self.cookies.pop(name.strip(), None)
                else:
                    self.cookies[name.strip()] = (value.strip(), cookie_path)
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


def render_links(body, maximized=0):
    """The page's links to the user's tabs, and to the first few maximized portlets.

    Only links that render and change nothing: the links that minimize a portlet or open its
    edit mode are left out.
    """
    tabs, portlets = [], []
    for href in re.findall(r'href="(/uPortal/[^"]*render\.uP)[^"]*"', body):
        href = html.unescape(href)
        if re.fullmatch(r'/uPortal/f/[^/?#]+/normal/render\.uP', href) and href not in tabs:
            tabs.append(href)
        elif re.fullmatch(r'/uPortal/f/[^/?#]+/p/[^/?#]+/max/render\.uP', href) and href not in portlets:
            portlets.append(href)
    return tabs + portlets[:maximized]


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
        pages = render_links(body, maximized=2)
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


def load(logs, seconds=int(os.environ.get('CHECK_LOAD_SECONDS', '180')), workers=16):
    # Each user's pages once, one after the other, so that the parallel loads below don't race
    # to create the user's portlet entities, which fails in its own way
    for user in USERS:
        client = Client()
        _, body, _, _ = login(client, user)
        for page in render_links(body):
            client.get(page)
        client.get('/uPortal/Logout', follow=False)
        client.close()

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
                pages = render_links(body)
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


def race(_logs, attempts=int(os.environ.get('CHECK_RACE_ATTEMPTS', '500')), probes=12):
    """Sends page requests of a signed-in session around the moment its /Logout is sent.

    The bug needs a page request that reads the person from the session just before the logout
    ends the session and asks for the session just after. So each attempt sends the probes
    between 2.5 ms before and 1.5 ms after the logout, and every session that a probe started is
    then asked for a page: does it show the logged-out user?
    """
    user, name = 'student', USERS['student']

    def new_session_id(set_cookies, old):
        for sc in set_cookies:
            m = re.match(r'\s*JSESSIONID=([^;]+)', sc)
            if m and m.group(1) != old:
                return m.group(1)
        return None

    latencies = []
    counts = collections.Counter()
    by_offset = collections.defaultdict(collections.Counter)
    stale = []
    rng = random.Random(int(os.environ.get('CHECK_RACE_SEED', '1')))
    for attempt in range(attempts):
        client = Client()
        status, body, path, _ = login(client, user)
        if status != 200 or name not in body:
            counts['sign-in failed'] += 1
            client.close()
            continue
        old = {name_: value for name_, (value, cookie_path) in client.cookies.items()
               if cookie_path.startswith('/uPortal') or cookie_path == '/'}
        client.close()
        offsets = [rng.uniform(-0.0025, 0.0015) for _ in range(probes)]
        results = [None] * probes
        start_at = time.time() + 0.02

        def probe(k, offsets=offsets, start_at=start_at, old=old, results=results):
            c = Client(old)
            try:
                c.connect()
                delay = start_at + 0.0025 + offsets[k] - time.time()
                if delay > 0:
                    time.sleep(delay)
                results[k] = c.get(WELCOME, follow=False)
            except Exception as e:  # noqa: BLE001
                results[k] = e
            finally:
                c.close()

        def logout(start_at=start_at, old=old):
            c = Client(old)
            try:
                c.connect()
                delay = start_at + 0.0025 - time.time()
                if delay > 0:
                    time.sleep(delay)
                sent = time.time()
                c.get('/uPortal/Logout', follow=False)
                latencies.append(time.time() - sent)
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
            bucket = f'{round(offsets[k] * 2000) / 2:+.1f} ms'
            if not isinstance(result, tuple):
                counts['probe failed'] += 1
                continue
            p_status, p_body, _, p_cookies = result
            sid = new_session_id(p_cookies, old.get('JSESSIONID'))
            shows = 'page of the user' if name in p_body else 'no user name in it'
            if sid is None:
                outcome = f'answer {p_status}, {shows}, no new session'
            else:
                check = Client({'JSESSIONID': sid})
                c_status, c_body, c_path, _ = check.get(WELCOME)
                if name in c_body:
                    outcome = 'NEW SESSION SHOWS THE LOGGED-OUT USER'
                    if len(stale) < 10:
                        stale.append({
                            'attempt': attempt,
                            'probe sent after logout (ms)': round(offsets[k] * 1000, 2),
                            'probe answer': f'{p_status}, {shows}',
                            'next page of that session': f'{c_status} {c_path}, shows {name}',
                            'REST user of that session': session_user(check),
                        })
                else:
                    outcome = f'answer {p_status}, {shows}, new session whose next page is the guest page'
                check.close()
            counts[outcome] += 1
            by_offset[bucket]['new session' if sid else 'old session'] += 1
    return {
        'attempts': attempts,
        'page requests per attempt': probes,
        'median time of /Logout (ms)': round(statistics.median(latencies) * 1000, 2) if latencies else None,
        'outcomes': dict(counts),
        'probes by send time relative to the logout': {
            k: dict(v) for k, v in sorted(by_offset.items(), key=lambda kv: float(kv[0].split()[0]))},
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
