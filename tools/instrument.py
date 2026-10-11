#!/usr/bin/env python3
"""Idempotent source instrumenter: adds function tracing (rally3d.core.Trace) and swallowed-exception logging (RLog.caught).

Run from the rally3d folder after adding/changing game code:   python tools/instrument.py
 - every non-trivial method of the game classes gets  `/*T*/ final long $t = Trace.in(ID); try { ... } finally { Trace.out(ID, $t); }`
 - every catch block gets  `RLog.caught("Class.method", e);` as its first statement (InterruptedException excluded)
 - source/game/rally3d/core/FnTable.java (function names + slow thresholds) is regenerated; existing ids are kept stable.
Already instrumented methods (marker /*T*/) and catches (marker RLog.caught) are skipped, so running it twice is safe.
Not instrumented on purpose: constructors, tiny methods (< 90 chars), hot per-frame math (HOT_NAMES), rasterizer (javax/microedition/m3g, lcdui), data classes.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'source')
TABLE = os.path.join(SRC, 'game', 'rally3d', 'core', 'FnTable.java')

# whole files: no method tracing (still get catch logging)
NO_METHODS = re.compile(r'(javax/microedition/m3g/|javax/microedition/lcdui/|/Vec3\.java|/BoundingBox\.java|/CarState\.java|/CameraState\.java|'
                        r'/RaceState\.java|/SegmentMask\.java|/TrackSegment\.java|/VertexColors\.java|/Texts\.java|/Localization\.java|'
                        r'/Mesh3D\.java|/Billboard\.java|/Fxaa\.java|rally3d/android/(GpuUi|GpuRenderer|Gl)\.java|rally3d/core/(Trace|FnTable|RLog)\.java)')
SKIP_FILES = re.compile(r'(rally3d/core/(Trace|FnTable|RLog)\.java|rally3d/android/(AndroidLog|Watchdog)\.java)$')
HOT_NAMES = {'groundHeight', 'triangleHeight', 'pointInQuad', 'meshHeightAt', 'surfaceScore', 'blendProfile', 'computeCorners', 'firstSegment',
             'addSegments', 'findSegments', 'resolveCollisions', 'renderWheel', 'updateWheelSpin', 'formatTime', 'writeInt', 'readInt',
             'writeFixed', 'readFixed', 'drawRect', 'drawPanel', 'drawStatBar', 'layoutText', 'wrapLine', 'drawTextBlock', 'updateOpponentGround',
             'updateWheels', 'shade', 'vignette', 'smooth', 'clamp', 'parse', 'mapKey', 'moveJoy', 'lx', 'ly'}
BLOCKING = re.compile(r'(?i)(menu|loop|^run$|wait|^show|confirm|syncstart|session|^start|resume|introsweep|exchange|sleep|poll|edit|screen|highscore|select|publish|^accept|^connect|^join|^host|discover|listen|read(Loop)?$)')
SLOW_MS = 300
MIN_BODY = 90
CONTROL = ('if', 'for', 'while', 'switch', 'catch', 'synchronized', 'try', 'else', 'do', 'finally')


def load_ids():
    ids = []
    if os.path.exists(TABLE):
        txt = open(TABLE, encoding='utf-8').read()
        m = re.search(r'NAMES\s*=\s*\{(.*?)\};', txt, re.S)
        if m:
            ids = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
    return ids


def strip_comments(s):
    s = re.sub(r'/\*.*?\*/', ' ', s, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', s).strip()


def scan(src):
    """Yield structure events. Returns (methods, catches): methods = [(name_key, class, open, close, header)], catches = [(open, var, type, class.method)]"""
    n = len(src)
    i = 0
    stack = []          # entries: dict(kind='class'|'method'|'block', name=..., open=pos, header=...)
    last_delim = 0
    methods, catches = [], []
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        if two == '//':
            j = src.find('\n', i); i = n if j < 0 else j; continue
        if two == '/*':
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == '\\' else 1
            i += 1; continue
        if c == "'":
            i += 1
            while i < n and src[i] != "'":
                i += 2 if src[i] == '\\' else 1
            i += 1; continue
        if c == ';' or c == '}' and False:
            last_delim = i + 1
        if c == '{':
            header = strip_comments(src[last_delim:i])
            top = stack[-1] if stack else None
            kind = 'block'; name = None
            cm = re.search(r'\b(class|interface|enum)\s+(\w+)', header)
            if cm and not re.search(r'[=(]\s*$', header):
                kind = 'class'; name = cm.group(2)
            elif re.search(r'\bnew\s+[\w.<>\[\], ?]+\([^;{]*\)\s*$', header, re.S):
                kind = 'class'; name = 'anon'
            elif (top is None or top['kind'] == 'class') and re.search(r'\)\s*(throws\s+[\w.,\s]+)?$', header):
                first = re.match(r'\s*(?:@\w+\s+)*(?:[\w<>\[\],.? ]+\s+)?(\w+)\s*\(', header)
                mname = first.group(1) if first else None
                if mname and mname not in CONTROL:
                    kind = 'method'; name = mname
            entry = dict(kind=kind, name=name, open=i, header=header, cls=None, method=None)
            # class / method context for catch naming
            cls = next((e['name'] for e in reversed(stack) if e['kind'] == 'class' and e['name'] != 'anon'), '?')
            entry['cls'] = name if kind == 'class' and name != 'anon' else cls
            entry['method'] = name if kind == 'method' else next((e['method'] for e in reversed(stack) if e['method']), '<init>')
            if kind == 'method':
                # constructor? name equals enclosing class name and no return type
                pre = re.sub(r'@\w+(\([^)]*\))?\s*', '', header[:header.rfind(name + '(') if (name + '(') in header else header.find(name)])
                pre = re.sub(r'\b(public|private|protected|static|final|synchronized)\b', '', pre).strip()
                is_ctor = (name == cls and pre == '')
                entry['ctor'] = is_ctor
            stack.append(entry)
            # catch blocks
            km = re.search(r'\bcatch\s*\(([^)]*?)\s+(\w+)\s*\)\s*$', header)
            if km:
                catches.append((i, km.group(2), km.group(1).strip(), '%s.%s' % (entry['cls'], entry['method'])))
            last_delim = i + 1
            i += 1; continue
        if c == '}':
            if stack:
                e = stack.pop()
                if e['kind'] == 'method' and not e.get('ctor'):
                    methods.append((e['name'], e['cls'], e['open'], i, e['header']))
            last_delim = i + 1
            i += 1; continue
        i += 1
    return methods, catches


def sig(header, name):
    m = re.search(r'\b' + re.escape(name) + r'\s*\((.*)\)\s*(?:throws[^{]*)?$', header, re.S)
    params = m.group(1).strip() if m else ''
    if not params:
        return '()'
    out, depth, cur = [], 0, ''
    for ch in params:
        if ch == '<': depth += 1
        elif ch == '>': depth -= 1
        if ch == ',' and depth == 0:
            out.append(cur); cur = ''
        else:
            cur += ch
    out.append(cur)
    types = []
    for p in out:
        p = re.sub(r'@\w+\s*', '', p).replace('final ', '').strip()
        parts = p.rsplit(None, 1)
        types.append(re.sub(r'\s+', '', parts[0] if len(parts) == 2 else p))
    return '(' + ','.join(types) + ')'


def process(path, rel, ids, idmap):
    src = open(path, encoding='utf-8').read()
    methods, catches = scan(src)
    edits = []   # (pos, text)  applied from the end
    mcount = ccount = 0
    if not NO_METHODS.search(rel):
        for name, cls, o, c, header in methods:
            if name in HOT_NAMES or re.search(r'\b(abstract|native)\b', header):
                continue
            body = src[o + 1:c]
            if body.lstrip().startswith('/*T*/'):
                continue
            if len(re.sub(r'\s+', '', body)) < MIN_BODY:
                continue
            key = '%s.%s%s' % (cls, name, sig(header, name))
            if key not in idmap:
                idmap[key] = len(ids); ids.append(key)
            fid = idmap[key]
            edits.append((o + 1, ' /*T*/ final long $t = rally3d.core.Trace.in(%d); try {' % fid))
            edits.append((c, ' } finally { rally3d.core.Trace.out(%d, $t); } ' % fid))
            mcount += 1
    for o, var, typ, where in catches:
        if 'InterruptedException' in typ:
            continue
        after = src[o + 1:o + 80]
        if 'RLog.caught(' in after.split('\n')[0] or after.lstrip().startswith('rally3d.core.RLog.caught('):
            continue
        edits.append((o + 1, ' rally3d.core.RLog.caught("%s", %s);' % (where, var)))
        ccount += 1
    if not edits:
        return 0, 0
    for pos, text in sorted(edits, key=lambda e: -e[0]):
        src = src[:pos] + text + src[pos:]
    open(path, 'w', encoding='utf-8', newline='').write(src)
    return mcount, ccount


def write_table(ids):
    def lit(s): return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'
    slow = []
    for k in ids:
        meth = k.split('(')[0].split('.')[-1]
        slow.append('Long.MAX_VALUE' if BLOCKING.search(meth) else '%dL * 1000000L' % SLOW_MS)
    slow.append('Long.MAX_VALUE')
    with open(TABLE, 'w', encoding='utf-8', newline='') as f:
        f.write('package rally3d.core;\n\n/** GENERATED by tools/instrument.py - do not edit. Function names for Trace and per-function "slow call" thresholds. */\n')
        f.write('final class FnTable {\n    private FnTable() {}\n    static final String[] NAMES = {\n')
        for k in ids: f.write('        %s,\n' % lit(k))
        f.write('        "?"\n    };\n    static final long[] SLOW_NS = {\n')
        for s in slow: f.write('        %s,\n' % s)
        f.write('    };\n}\n')


def main():
    ids = load_ids()
    if ids and ids[-1] == '?': ids.pop()
    idmap = {k: i for i, k in enumerate(ids)}
    tm = tc = 0
    for base, _, files in os.walk(SRC):
        for fn in sorted(files):
            if not fn.endswith('.java'): continue
            path = os.path.join(base, fn)
            rel = path.replace('\\', '/')
            if '/source/pc/' in rel or '/source/lib/' in rel or SKIP_FILES.search(rel): continue
            m, c = process(path, rel, ids, idmap)
            if m or c: print('%-70s methods=%d catches=%d' % (rel[len(SRC) + 1:], m, c))
            tm += m; tc += c
    write_table(ids)
    print('instrumented %d methods, %d catch blocks; table has %d functions' % (tm, tc, len(ids)))


if __name__ == '__main__':
    main()
