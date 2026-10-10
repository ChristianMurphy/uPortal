// Prototype of the P5-5 "removed-Servlet-API bytecode scanner" (uPortal 6.0 Jakarta spike).
//
// ASM-free: uses the JDK 25 java.lang.classfile API. Run with JDK 25 in source-launch mode:
//   java JakartaLinkScan.java scan   --api <tomcat11 lib jars...> --old-api <tomcat 8.5 lib jars...>
//                                    --shared <shared/lib jars...> --webapp <dir> [--webapp <dir> ...]
//   java JakartaLinkScan.java census <dir-or-jar> [...]          (javax/jakarta namespace census per jar)
//
// "scan" does a linkage check restricted to container-provided namespaces:
//   jakarta.servlet(.jsp), jakarta.el, jakarta.websocket, jakarta.annotation,
//   jakarta.security.auth.message, plus the shared/lib namespaces javax.portlet and org.apache.pluto.
// For every Fieldref/Methodref/InterfaceMethodref and Class constant in each webapp class, it resolves the
// member through the webapp + shared/lib + Tomcat 11 API class hierarchy (JVM resolution rules, exact
// descriptor). Anything that does not resolve is a hit. A hit is labelled REMOVED when the same member
// exists in the Tomcat 8.5 javax API (renamed to jakarta), i.e. it was removed between Servlet 4.0/JSP 2.3/
// EL 3.0/WebSocket 1.1 and Tomcat 11 (Servlet 6.1/JSP 4.0/EL 6.0/WebSocket 2.2).
// It also reports concrete webapp classes that implement container interfaces / extend container abstract
// classes but lack an implementation of a method that is abstract in Tomcat 11 (AbstractMethodError risk),
// javax.servlet/el/websocket references left behind after the transform, and jakarta.* references to
// packages that neither Tomcat 11 nor the webapp provides.
import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.reflect.AccessFlag;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;
import java.util.function.UnaryOperator;

public class JakartaLinkScan {

    record Member(String name, String desc, boolean isAbstract, boolean isStatic, boolean isApi) {}

    static final class Cls {
        final String name, superName, origin;
        final List<String> ifaces;
        final boolean isInterface, isAbstract; boolean isApi;
        final Map<String, Member> methods = new HashMap<>(); // key name+desc
        final Set<String> fields = new HashSet<>();          // key name+desc
        final Set<String> apiFields = new HashSet<>();
        Cls(String name, String superName, List<String> ifaces, boolean isInterface, boolean isAbstract, String origin) {
            this.name = name; this.superName = superName; this.ifaces = ifaces;
            this.isInterface = isInterface; this.isAbstract = isAbstract; this.origin = origin;
        }
    }

    record Ref(String kind, String owner, String name, String desc, String fromClass, String origin) {}

    // ------------------------------------------------------------------ reading
    interface Sink { void accept(String origin, String entry, byte[] bytes); }

    static void walk(Path p, Sink sink) throws IOException {
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : (Iterable<Path>) s::iterator) {
                    String n = f.toString();
                    if (n.endsWith(".class") && !n.contains("META-INF/versions/")) {
                        sink.accept(originOf(p, f), p.relativize(f).toString(), Files.readAllBytes(f));
                    } else if (n.endsWith(".jar")) {
                        try (InputStream in = Files.newInputStream(f)) { readZip(in, f.getFileName().toString(), sink); }
                    }
                }
            }
        } else {
            try (InputStream in = Files.newInputStream(p)) { readZip(in, p.getFileName().toString(), sink); }
        }
    }

    static String originOf(Path root, Path f) {
        String rel = root.relativize(f).toString();
        return rel.startsWith("WEB-INF/classes") ? "WEB-INF/classes" : rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
    }

    static void readZip(InputStream in, String origin, Sink sink) throws IOException {
        ZipInputStream z = new ZipInputStream(in);
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            String n = e.getName();
            if (n.endsWith(".class") && !n.startsWith("META-INF/versions/")) {
                sink.accept(origin, n, z.readAllBytes());
            } else if (n.endsWith(".jar")) {
                readZip(new ByteArrayInputStream(z.readAllBytes()), origin + "!" + n, sink);
            }
        }
    }

    static ClassModel parse(byte[] b) {
        try { return ClassFile.of().parse(b); } catch (Throwable t) { return null; }
    }

    static Cls model(ClassModel cm, String origin, UnaryOperator<String> rename) {
        String name = rename.apply(cm.thisClass().asInternalName());
        String sup = cm.superclass().map(c -> rename.apply(c.asInternalName())).orElse(null);
        List<String> ifs = cm.interfaces().stream().map(c -> rename.apply(c.asInternalName())).toList();
        boolean isIf = cm.flags().has(AccessFlag.INTERFACE);
        Cls c = new Cls(name, sup, ifs, isIf, cm.flags().has(AccessFlag.ABSTRACT), origin);
        for (MethodModel m : cm.methods()) {
            String d = rename.apply(m.methodType().stringValue());
            boolean api = (m.flags().has(AccessFlag.PUBLIC) || m.flags().has(AccessFlag.PROTECTED)) && !m.flags().has(AccessFlag.SYNTHETIC);
            c.methods.put(m.methodName().stringValue() + d,
                    new Member(m.methodName().stringValue(), d, m.flags().has(AccessFlag.ABSTRACT), m.flags().has(AccessFlag.STATIC), api));
        }
        for (FieldModel f : cm.fields()) {
            String k = f.fieldName().stringValue() + rename.apply(f.fieldType().stringValue());
            c.fields.add(k);
            if (f.flags().has(AccessFlag.PUBLIC) || f.flags().has(AccessFlag.PROTECTED)) c.apiFields.add(k);
        }
        c.isApi = cm.flags().has(AccessFlag.PUBLIC) && !name.contains("$1") && !name.contains("$2") && !name.contains("$3");
        return c;
    }

    static Map<String, Cls> loadIndex(List<String> paths, UnaryOperator<String> rename) throws IOException {
        Map<String, Cls> idx = new HashMap<>();
        for (String p : paths) {
            walk(Paths.get(p), (origin, entry, bytes) -> {
                ClassModel cm = parse(bytes);
                if (cm != null) { Cls c = model(cm, origin, rename); idx.putIfAbsent(c.name, c); }
            });
        }
        return idx;
    }

    // ------------------------------------------------------------------ resolution
    static final List<String> SUMMARY = new ArrayList<>();
    static final String[] INTEREST = {
        "jakarta/servlet/", "jakarta/el/", "jakarta/websocket/", "jakarta/annotation/", "jakarta/security/auth/message/",
        "javax/portlet/", "org/apache/pluto/container/",
        // Tomcat internals are visible to webapps through the common loader (e.g. Spring Boot's embedded-Tomcat support)
        "org/apache/catalina/", "org/apache/coyote/", "org/apache/tomcat/", "org/apache/jasper/", "org/apache/juli/", "org/apache/naming/"
    };
    static boolean interesting(String n) { for (String p : INTEREST) if (n.startsWith(p)) return true; return false; }
    static final Set<String> OBJECT_METHODS = Set.of("hashCode()I", "equals(Ljava/lang/Object;)Z", "toString()Ljava/lang/String;",
            "getClass()Ljava/lang/Class;", "notify()V", "notifyAll()V", "wait()V", "wait(J)V", "wait(JI)V", "clone()Ljava/lang/Object;", "finalize()V");

    final List<Map<String, Cls>> layers; // lookup order
    JakartaLinkScan(List<Map<String, Cls>> layers) { this.layers = layers; }
    Cls find(String n) {
        for (Map<String, Cls> l : layers) { Cls c = l.get(n); if (c != null) return c; }
        return jdk(n);
    }

    // ---- exact JDK class models, loaded lazily from the running JDK's jrt:/ image
    static final Map<String, Optional<Cls>> JDK = new HashMap<>();
    static FileSystem JRT;
    static synchronized Cls jdk(String n) {
        if (!(n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("jdk/") || n.startsWith("sun/") || n.startsWith("com/sun/") || n.startsWith("org/w3c/") || n.startsWith("org/xml/"))) return null;
        return JDK.computeIfAbsent(n, k -> {
            try {
                if (JRT == null) JRT = FileSystems.getFileSystem(java.net.URI.create("jrt:/"));
                int slash = k.lastIndexOf('/');
                Path pkg = JRT.getPath("/packages/" + k.substring(0, slash).replace('/', '.'));
                if (!Files.isDirectory(pkg)) return Optional.empty();
                try (Stream<Path> mods = Files.list(pkg)) {
                    for (Path m : (Iterable<Path>) mods::iterator) {
                        Path cf = JRT.getPath("/modules/" + m.getFileName() + "/" + k + ".class");
                        if (Files.exists(cf)) return Optional.of(model(ClassFile.of().parse(Files.readAllBytes(cf)), "JDK", x -> x));
                    }
                }
            } catch (Exception e) { /* fall through */ }
            return Optional.empty();
        }).orElse(null);
    }

    /** @return 1 found, 0 missing, -1 unknown (hierarchy leaves our model, e.g. into java.*), -2 owner class missing */
    int resolve(String owner, String key, boolean method, Set<String> seen, boolean[] touchedInterest) {
        if (owner == null || !seen.add(owner)) return 0;
        if (interesting(owner)) touchedInterest[0] = true;
        if (owner.startsWith("[")) return 1; // array clone() etc.
        Cls c = find(owner);
        if (c == null) return -2;
        if (method ? c.methods.containsKey(key) : c.fields.contains(key)) return 1;
        if (method && OBJECT_METHODS.contains(key)) return 1;
        int best = 0;
        List<String> parents = new ArrayList<>();
        if (c.superName != null) parents.add(c.superName);
        parents.addAll(c.ifaces);
        if (c.isInterface && method) parents.add("java/lang/Object");
        for (String p : parents) {
            int r = resolve(p, key, method, seen, touchedInterest);
            if (r == 1) return 1;
            if (r == -1) best = -1;
        }
        return best;
    }

    // ------------------------------------------------------------------ scan
    static final Pattern LEFT_JAVAX = Pattern.compile("javax[/.](servlet|el|websocket)[/.]");
    static final Pattern JAKARTA_PKG = Pattern.compile("jakarta/([a-z0-9]+(?:/[a-z0-9]+)?)/[A-Za-z0-9_$]+");

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("census")) { census(Arrays.copyOfRange(a, 1, a.length)); return; }
        if (a.length > 0 && a[0].equals("sig")) { sig(a[1], Arrays.copyOfRange(a, 2, a.length)); return; }
        List<String> api = new ArrayList<>(), old = new ArrayList<>(), shared = new ArrayList<>(), webapps = new ArrayList<>();
        List<String> cur = null;
        for (int i = 1; i < a.length; i++) {
            switch (a[i]) {
                case "--api" -> cur = api;
                case "--old-api" -> cur = old;
                case "--shared" -> cur = shared;
                case "--webapp" -> cur = webapps;
                default -> cur.add(a[i]);
            }
        }
        Map<String, Cls> apiIdx = loadIndex(api, s -> s);
        // EE8 reference model, renamed with the Tomcat-provided rules (same as the TOMCAT profile's servlet/el/websocket/annotation subset)
        Pattern ren = Pattern.compile("javax([/.])(servlet|el|websocket|annotation|security[/.]auth[/.]message)([/.])");
        Map<String, Cls> oldIdx = loadIndex(old, s -> ren.matcher(s).replaceAll("jakarta$1$2$3"));
        Map<String, Cls> sharedIdx = loadIndex(shared, s -> s);
        System.out.printf("# API classes: tomcat11=%d  tomcat8.5(renamed)=%d  shared/lib=%d%n", apiIdx.size(), oldIdx.size(), sharedIdx.size());
        apiDiff(apiIdx, oldIdx);
        for (String w : webapps) scanWebapp(Paths.get(w), apiIdx, oldIdx, sharedIdx);
        System.out.println("\n## SUMMARY\n| webapp | classes | removed-API refs (distinct members/classes) | other unresolvable container refs | jars with removed-API refs | jars with classes lacking new abstract methods |\n|---|---|---|---|---|---|");
        SUMMARY.forEach(System.out::println);
    }

    /** Print the member-level diff EE8(renamed) -> Tomcat 11 for the classes that survive, plus removed classes. */
    static void apiDiff(Map<String, Cls> api, Map<String, Cls> old) {
        System.out.println("\n## Public container API removed between Tomcat 8.5 (Servlet 3.1/JSP 2.3/EL 3.0/WebSocket 1.1, renamed to jakarta) and Tomcat 11 (Servlet 6.1/Pages 4.0/EL 6.0/WebSocket 2.2)");
        List<String> lines = new ArrayList<>();
        for (Cls o : old.values()) {
            if (!o.name.startsWith("jakarta/") || !o.isApi) continue;
            Cls n = api.get(o.name);
            if (n == null) { lines.add("CLASS   " + o.name.replace('/', '.')); continue; }
            for (Member m : o.methods.values()) {
                if (m.name.equals("<clinit>") || !m.isApi) continue;
                if (!n.methods.containsKey(m.name + m.desc)) lines.add("METHOD  " + o.name.replace('/', '.') + "." + m.name + m.desc);
            }
            for (String f : o.apiFields) if (!n.fields.contains(f)) lines.add("FIELD   " + o.name.replace('/', '.') + "." + f);
        }
        Collections.sort(lines);
        lines.forEach(l -> System.out.println("  " + l));
        System.out.println("\n## Methods abstract in Tomcat 11 public container types that were not abstract in Tomcat 8.5 (AbstractMethodError risk for direct implementors)");
        List<String> added = new ArrayList<>();
        for (Cls n : api.values()) {
            Cls o = old.get(n.name);
            if (!n.isApi || !n.name.startsWith("jakarta/")) continue;
            for (Member m : n.methods.values()) {
                if (!m.isAbstract || !m.isApi) continue;
                Member om = o == null ? null : o.methods.get(m.name + m.desc);
                if (om == null || !om.isAbstract) added.add(n.name.replace('/', '.') + "." + m.name + m.desc);
            }
        }
        Collections.sort(added);
        added.forEach(l -> System.out.println("  NEW-ABSTRACT " + l));
    }

    static void scanWebapp(Path root, Map<String, Cls> api, Map<String, Cls> old, Map<String, Cls> shared) throws IOException {
        Map<String, Cls> app = new HashMap<>();
        List<Ref> refs = new ArrayList<>();
        Map<String, Set<String>> leftovers = new TreeMap<>();      // origin -> javax.servlet... strings
        Map<String, Set<String>> jakartaPkgs = new TreeMap<>();    // jakarta pkg -> origins
        int[] nClasses = {0};
        walk(root, (origin, entry, bytes) -> {
            ClassModel cm = parse(bytes);
            if (cm == null) return;
            nClasses[0]++;
            Cls c = model(cm, origin, s -> s);
            app.putIfAbsent(c.name, c);
            for (PoolEntry pe : cm.constantPool()) {
                if (pe instanceof MemberRefEntry mr) {
                    String kind = pe instanceof FieldRefEntry ? "F" : "M";
                    refs.add(new Ref(kind, mr.owner().asInternalName(), mr.name().stringValue(), mr.type().stringValue(), c.name, origin));
                } else if (pe instanceof ClassEntry ce) {
                    refs.add(new Ref("C", ce.asInternalName(), "", "", c.name, origin));
                } else if (pe instanceof Utf8Entry u) {
                    String s = u.stringValue();
                    if (s.indexOf("javax") >= 0) {
                        Matcher m = LEFT_JAVAX.matcher(s);
                        if (m.find()) leftovers.computeIfAbsent(origin, k -> new TreeSet<>()).add(s.length() > 120 ? s.substring(0, 120) + "..." : s);
                    }
                    if (s.indexOf("jakarta/") >= 0) {
                        Matcher m = JAKARTA_PKG.matcher(s);
                        while (m.find()) jakartaPkgs.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(origin);
                    }
                }
            }
        });
        JakartaLinkScan r = new JakartaLinkScan(List.of(app, shared, api));
        System.out.printf("%n==================================================================%n# WEBAPP %s  (%d classes)%n", root.getFileName(), nClasses[0]);

        // 1. unresolved member / class refs touching container namespaces
        Map<String, Set<String>> hits = new TreeMap<>(); // label -> origins/classes
        Set<String> seenKey = new HashSet<>();
        for (Ref ref : refs) {
            String k = ref.kind + ref.owner + "." + ref.name + ref.desc + "|" + ref.fromClass;
            if (!seenKey.add(k)) continue;
            if (ref.kind.equals("C")) {
                String n = ref.owner;
                while (n.startsWith("[")) n = n.substring(1);
                if (n.startsWith("L") && n.endsWith(";")) n = n.substring(1, n.length() - 1);
                if (interesting(n) && r.find(n) == null) {
                    String label = (old.containsKey(n) ? "REMOVED-CLASS " : "MISSING-CLASS ") + n.replace('/', '.');
                    hits.computeIfAbsent(label, x -> new TreeSet<>()).add(ref.origin + " :: " + ref.fromClass.replace('/', '.'));
                }
                continue;
            }
            boolean method = ref.kind.equals("M");
            String key = ref.name + ref.desc;
            boolean[] touched = {false};
            int res = r.resolve(ref.owner, key, method, new HashSet<>(), touched);
            if (res == 1 || res == -1 || !touched[0]) continue;
            if (res == -2 && !interesting(ref.owner)) continue;
            // was it there in EE8 (resolve against the renamed EE8 model in place of Tomcat 11)?
            JakartaLinkScan r8 = new JakartaLinkScan(List.of(app, shared, old));
            int res8 = r8.resolve(ref.owner, key, method, new HashSet<>(), new boolean[1]);
            String label = (res8 == 1 ? "REMOVED " : res == -1 ? "UNKNOWN " : "MISSING ") + (method ? "method " : "field ")
                    + ref.owner.replace('/', '.') + "." + ref.name + ref.desc;
            hits.computeIfAbsent(label, x -> new TreeSet<>()).add(ref.origin + " :: " + ref.fromClass.replace('/', '.'));
        }
        System.out.println("## Unresolvable references into container/shared namespaces (" + hits.size() + ")");
        hits.forEach((l, s) -> {
            Set<String> jars = s.stream().map(x -> x.substring(0, x.indexOf(" :: "))).collect(Collectors.toCollection(TreeSet::new));
            System.out.println("  " + l);
            System.out.println("      in " + jars + "  e.g. " + s.iterator().next());
        });

        // 2. AbstractMethodError risk
        System.out.println("## Concrete classes missing implementations of abstract container methods");
        int amCount = 0, preExisting = 0;
        Set<String> newAbstractJars = new TreeSet<>();
        for (Cls c : app.values()) {
            if (c.isInterface || c.isAbstract) continue;
            Map<String, String> abstracts = new TreeMap<>();
            Set<String> concrete = new HashSet<>();
            collect(r, c.name, abstracts, concrete, new HashSet<>(), false);
            for (Map.Entry<String, String> e : abstracts.entrySet()) {
                if (concrete.contains(e.getKey())) continue;
                if (!interesting(e.getValue())) continue;
                Cls o = old.get(e.getValue());
                boolean pre = o != null && o.methods.containsKey(e.getKey()) && o.methods.get(e.getKey()).isAbstract;
                if (pre) { preExisting++; continue; }
                amCount++;
                newAbstractJars.add(c.origin);
                System.out.println("  " + c.name.replace('/', '.') + " [" + c.origin + "] lacks " + e.getValue().replace('/', '.') + "." + e.getKey() + " (abstract in Tomcat 11, not in Tomcat 8.5)");
            }
        }
        if (amCount == 0) System.out.println("  none");
        System.out.println("  (+" + preExisting + " gaps that already existed against Tomcat 8.5's Servlet 3.1 API, not listed)");

        long removedRefs = hits.keySet().stream().filter(l -> l.startsWith("REMOVED")).count();
        long missingRefs = hits.keySet().stream().filter(l -> l.startsWith("MISSING")).count();
        Set<String> removedJars = new TreeSet<>();
        hits.forEach((l, v) -> { if (l.startsWith("REMOVED")) v.forEach(x -> removedJars.add(x.substring(0, x.indexOf(" :: ")))); });
        SUMMARY.add(String.format("| %s | %d | %d | %d | %s | %s |", root.getFileName(), nClasses[0], removedRefs, missingRefs,
                removedJars.isEmpty() ? "-" : String.join(", ", removedJars), newAbstractJars.isEmpty() ? "-" : String.join(", ", newAbstractJars)));
        // 3. leftovers
        System.out.println("## javax.servlet/el/websocket strings left in class files after transform");
        if (leftovers.isEmpty()) System.out.println("  none");
        leftovers.forEach((o, s) -> System.out.println("  " + o + ": " + s.size() + " e.g. " + s.stream().limit(3).toList()));

        // 4. jakarta packages referenced vs provided
        System.out.println("## jakarta.* packages referenced (provided by: T=Tomcat 11, W=webapp itself, -=nobody)");
        Set<String> apiPkgs = new HashSet<>(), appPkgs = new HashSet<>();
        api.keySet().forEach(n -> { if (n.lastIndexOf('/') > 0) apiPkgs.add(n.substring(0, n.lastIndexOf('/'))); });
        app.keySet().forEach(n -> { if (n.lastIndexOf('/') > 0) appPkgs.add(n.substring(0, n.lastIndexOf('/'))); });
        jakartaPkgs.forEach((p, origins) -> {
            String full = "jakarta/" + p;
            boolean t = apiPkgs.stream().anyMatch(x -> x.equals(full) || x.startsWith(full + "/"));
            boolean w = appPkgs.stream().anyMatch(x -> x.equals(full) || x.startsWith(full + "/"));
            System.out.printf("  %-34s %s%s  referenced from %d jars%s%n", "jakarta." + p.replace('/', '.'), t ? "T" : "", w ? "W" : (t ? "" : "-"),
                    origins.size(), (!t && !w) ? " " + origins.stream().limit(6).toList() : "");
        });
    }

    static void collect(JakartaLinkScan r, String n, Map<String, String> abstracts, Set<String> concrete, Set<String> seen, boolean viaIface) {
        if (n == null || !seen.add(n)) return;
        Cls c = r.find(n);
        if (c == null) return;
        for (Member m : c.methods.values()) {
            if (m.isStatic || m.name.startsWith("<")) continue;
            String k = m.name + m.desc;
            if (m.isAbstract) abstracts.putIfAbsent(k, c.name); else concrete.add(k);
        }
        collect(r, c.superName, abstracts, concrete, seen, viaIface);
        for (String i : c.ifaces) collect(r, i, abstracts, concrete, seen, true);
    }

    /** sig <substring> <paths...>: classes whose member descriptors (not code) mention the substring, i.e. types
     *  that reflection/Introspector (e.g. Jasper's TagHandlerInfo) will try to load. */
    static void sig(String needle, String[] paths) throws IOException {
        for (String p : paths) walk(Paths.get(p), (origin, entry, bytes) -> {
            ClassModel cm = parse(bytes);
            if (cm == null) return;
            for (MethodModel m : cm.methods()) if (m.methodType().stringValue().contains(needle))
                System.out.println(origin + " :: " + cm.thisClass().asInternalName().replace('/', '.') + "." + m.methodName().stringValue() + m.methodType().stringValue()
                    + (m.flags().has(AccessFlag.PUBLIC) ? " [public]" : ""));
            for (FieldModel f : cm.fields()) if (f.fieldType().stringValue().contains(needle))
                System.out.println(origin + " :: " + cm.thisClass().asInternalName().replace('/', '.') + "#" + f.fieldName().stringValue() + " " + f.fieldType().stringValue());
        });
    }

    // ------------------------------------------------------------------ census
    static final Pattern NS = Pattern.compile("(javax|jakarta)[/.]([a-z0-9]+)(?:[/.]([a-z0-9]+))?");
    static final Set<String> TWO_LEVEL = Set.of("xml", "security", "management", "annotation", "transaction", "enterprise", "servlet");

    static void census(String[] paths) throws IOException {
        for (String p : paths) {
            Map<String, Map<String, Integer>> byOrigin = new TreeMap<>();
            walk(Paths.get(p), (origin, entry, bytes) -> {
                ClassModel cm = parse(bytes);
                if (cm == null) return;
                Set<String> nsInClass = new HashSet<>();
                for (PoolEntry pe : cm.constantPool()) {
                    if (!(pe instanceof Utf8Entry u)) continue;
                    String s = u.stringValue();
                    if (s.indexOf("javax") < 0 && s.indexOf("jakarta") < 0) continue;
                    Matcher m = NS.matcher(s);
                    while (m.find()) {
                        String k = m.group(1) + "." + m.group(2);
                        if (TWO_LEVEL.contains(m.group(2)) && m.group(3) != null && Character.isLowerCase(m.group(3).charAt(0))) k += "." + m.group(3);
                        nsInClass.add(k);
                    }
                }
                Map<String, Integer> mm = byOrigin.computeIfAbsent(origin, k -> new TreeMap<>());
                for (String k : nsInClass) mm.merge(k, 1, Integer::sum);
            });
            System.out.println("# CENSUS " + p);
            byOrigin.forEach((o, m) -> { if (!m.isEmpty()) System.out.println("  " + o + "\t" + m); });
        }
    }
}
