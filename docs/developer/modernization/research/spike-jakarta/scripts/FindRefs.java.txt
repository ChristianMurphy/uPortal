// usage: java FindRefs.java <owner-prefix> <member-name> <paths...>  -> prints classes with Methodref/InterfaceMethodref to owner*.member
import java.lang.classfile.*; import java.lang.classfile.constantpool.*; import java.nio.file.*; import java.io.*; import java.util.zip.*;
public class FindRefs {
  static String owner, member;
  public static void main(String[] a) throws Exception { owner=a[0]; member=a[1]; for (int i=2;i<a.length;i++) walk(Paths.get(a[i])); }
  static void walk(Path p) throws IOException {
    if (Files.isDirectory(p)) { try (var s = Files.walk(p)) { for (Path f : (Iterable<Path>) s::iterator) { String n=f.toString();
      if (n.endsWith(".class")) check(p.relativize(f).toString(), Files.readAllBytes(f)); else if (n.endsWith(".jar")) zip(Files.newInputStream(f), f.getFileName().toString()); } } }
    else zip(Files.newInputStream(p), p.getFileName().toString()); }
  static void zip(InputStream in, String o) throws IOException { var z=new ZipInputStream(in); ZipEntry e; while((e=z.getNextEntry())!=null) if(e.getName().endsWith(".class")) check(o+"!"+e.getName(), z.readAllBytes()); }
  static void check(String where, byte[] b) { try { var cm = ClassFile.of().parse(b);
    for (PoolEntry pe : cm.constantPool()) if (pe instanceof MemberRefEntry mr && mr.owner().asInternalName().startsWith(owner) && mr.name().stringValue().equals(member))
      System.out.println(where + " -> " + mr.owner().asInternalName() + "." + member + mr.type().stringValue()); } catch (Throwable t) {} }
}
