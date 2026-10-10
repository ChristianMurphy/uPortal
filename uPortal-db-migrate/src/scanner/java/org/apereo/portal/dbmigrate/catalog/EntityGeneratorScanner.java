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
package org.apereo.portal.dbmigrate.catalog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Build-time scanner: reads the JPA annotations of the uPortal entities and writes the generator
 * catalog ({@code generators.tsv}) that {@link GeneratorCatalog} loads.
 *
 * <p>Annotations are read reflectively by simple name, so the scanner works for {@code
 * javax.persistence} and {@code jakarta.persistence} alike and needs no JPA API at compile time.
 * When an entity declares a {@code @SequenceGenerator} and a {@code @TableGenerator} under the same
 * name (49 do in 5.17), the sequence is what Hibernate uses on every vendor: on vendors without
 * sequences it becomes a one-row table of the sequence's name (MOD-I23).
 *
 * <p>Usage: {@code EntityGeneratorScanner <output-file> <jar-or-class-dir>...}; the classes'
 * dependencies must be on the scanner's own classpath.
 */
public final class EntityGeneratorScanner {

    private final Map<String, List<Annotation>> generatorsByName = new TreeMap<>();
    private final List<String> lines = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private int entities;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: EntityGeneratorScanner <output-file> <jar-or-dir>...");
            System.exit(2);
        }
        List<String> classNames = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            classNames.addAll(candidates(new File(args[i])));
        }
        EntityGeneratorScanner scanner = new EntityGeneratorScanner();
        List<Class<?>> classes = new ArrayList<>();
        ClassLoader loader = EntityGeneratorScanner.class.getClassLoader();
        for (String name : new TreeSet<>(classNames)) {
            try {
                classes.add(Class.forName(name, false, loader));
            } catch (Throwable e) { // NOPMD: a class that cannot load is not a mapped entity
                scanner.problems.add("not loadable: " + name + " (" + e + ")");
            }
        }
        for (Class<?> c : classes) {
            scanner.collectGenerators(c);
        }
        for (Class<?> c : classes) {
            if (annotation(c, "Entity") != null) {
                scanner.entities++;
                scanner.collectId(c);
            }
        }
        Collections.sort(scanner.lines);
        Path out = Paths.get(args[0]);
        Files.createDirectories(out.getParent());
        try (PrintWriter w =
                new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println(
                    "# uPortal ID generator catalog, generated at build time from the JPA entity");
            w.println("# annotations by " + EntityGeneratorScanner.class.getName() + ".");
            w.println(
                    "# entities: " + scanner.entities + "; generated ids: " + scanner.lines.size());
            w.println("# generator\tkind\tallocationSize\ttable\tcolumn\tentity");
            scanner.lines.forEach(w::println);
        }
        scanner.problems.forEach(p -> System.out.println("EntityGeneratorScanner: " + p));
        System.out.println(
                "EntityGeneratorScanner: "
                        + scanner.entities
                        + " entities, "
                        + scanner.lines.size()
                        + " sequence-generated ids -> "
                        + out);
        // A mapped class that cannot be read would silently lose its generator
        boolean unreadable = false;
        for (String p : scanner.problems) {
            unreadable |= p.startsWith("not loadable");
        }
        if (unreadable || scanner.lines.isEmpty()) {
            throw new IllegalStateException(
                    "Entity classes could not be read; add their dependencies to the entityScan"
                            + " configuration");
        }
    }

    /** Classes whose bytecode mentions a JPA {@code @Entity} or {@code @MappedSuperclass}. */
    static List<String> candidates(File f) throws IOException {
        List<String> names = new ArrayList<>();
        if (f.isDirectory()) {
            Path root = f.toPath();
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".class"))
                        .forEach(
                                p -> {
                                    try {
                                        if (mentionsEntity(Files.readAllBytes(p))) {
                                            names.add(className(root.relativize(p).toString()));
                                        }
                                    } catch (IOException e) {
                                        throw new IllegalStateException(e);
                                    }
                                });
            }
        } else if (f.isFile()) {
            try (JarFile jar = new JarFile(f)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                        try (InputStream in = jar.getInputStream(e)) {
                            if (mentionsEntity(in.readAllBytes())) {
                                names.add(className(e.getName()));
                            }
                        }
                    }
                }
            }
        }
        return names;
    }

    private static boolean mentionsEntity(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.ISO_8859_1);
        return s.contains("/persistence/Entity;") || s.contains("/persistence/MappedSuperclass;");
    }

    private static String className(String path) {
        return path.substring(0, path.length() - ".class".length())
                .replace(File.separatorChar, '.')
                .replace('/', '.');
    }

    private void collectGenerators(Class<?> c) {
        List<AnnotatedElement> elements = new ArrayList<>();
        elements.add(c);
        try {
            Collections.addAll(elements, c.getDeclaredFields());
            Collections.addAll(elements, c.getDeclaredMethods());
        } catch (Throwable e) { // NOPMD: member types missing; class-level declarations still count
            problems.add("members of " + c.getName() + " not readable (" + e + ")");
        }
        for (AnnotatedElement el : elements) {
            for (Annotation a : el.getAnnotations()) {
                String n = a.annotationType().getSimpleName();
                if (n.equals("SequenceGenerators") || n.equals("TableGenerators")) {
                    for (Annotation inner : (Annotation[]) value(a, "value")) {
                        register(inner);
                    }
                } else if (n.equals("SequenceGenerator") || n.equals("TableGenerator")) {
                    register(a);
                }
            }
        }
    }

    private void register(Annotation a) {
        generatorsByName.computeIfAbsent((String) value(a, "name"), k -> new ArrayList<>()).add(a);
    }

    private void collectId(Class<?> entity) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            List<AnnotatedElement> members = new ArrayList<>();
            Collections.addAll(members, c.getDeclaredFields());
            Collections.addAll(members, c.getDeclaredMethods());
            AnnotatedElement id = null;
            for (AnnotatedElement m : members) {
                if (annotation(m, "Id") != null || annotation(m, "EmbeddedId") != null) {
                    id = m;
                    break;
                }
            }
            if (id == null) {
                continue;
            }
            if (c != entity && annotation(c, "Entity") != null) {
                return; // the id belongs to a parent entity (JOINED / SINGLE_TABLE subclass)
            }
            Annotation gv = annotation(id, "GeneratedValue");
            if (gv == null) {
                return;
            }
            String generator = (String) value(gv, "generator");
            Annotation sequence = null;
            for (Annotation g : generatorsByName.getOrDefault(generator, Collections.emptyList())) {
                if (g.annotationType().getSimpleName().equals("SequenceGenerator")) {
                    sequence = g;
                }
            }
            if (sequence == null) {
                // UUID and other non-database generators have no position to restore
                problems.add(
                        "no @SequenceGenerator for " + entity.getName() + " (" + generator + ")");
                return;
            }
            String sequenceName = (String) value(sequence, "sequenceName");
            if (sequenceName == null || sequenceName.isEmpty()) {
                sequenceName = generator;
            }
            Annotation column = annotation(id, "Column");
            String columnName = column == null ? null : (String) value(column, "name");
            if (columnName == null || columnName.isEmpty()) {
                columnName = memberName(id);
            }
            lines.add(
                    String.join(
                            "\t",
                            sequenceName,
                            "sequence",
                            String.valueOf(value(sequence, "allocationSize")),
                            tableName(entity),
                            columnName,
                            entity.getName()));
            return;
        }
    }

    private static String tableName(Class<?> entity) {
        Annotation table = annotation(entity, "Table");
        String name = table == null ? null : (String) value(table, "name");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        Annotation e = annotation(entity, "Entity");
        String entityName = e == null ? null : (String) value(e, "name");
        return entityName != null && !entityName.isEmpty() ? entityName : entity.getSimpleName();
    }

    private static String memberName(AnnotatedElement m) {
        if (m instanceof Method) {
            String n = ((Method) m).getName();
            String bare =
                    n.startsWith("get") ? n.substring(3) : n.startsWith("is") ? n.substring(2) : n;
            return Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
        }
        return ((java.lang.reflect.Field) m).getName();
    }

    private static Annotation annotation(AnnotatedElement el, String simpleName) {
        for (Annotation a : el.getAnnotations()) {
            String type = a.annotationType().getName();
            if (a.annotationType().getSimpleName().equals(simpleName)
                    && type.contains(".persistence.")) {
                return a;
            }
        }
        return null;
    }

    private static Object value(Annotation a, String attribute) {
        try {
            return a.annotationType().getMethod(attribute).invoke(a);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "No " + attribute + " on " + a.annotationType().getName(), e);
        }
    }
}
