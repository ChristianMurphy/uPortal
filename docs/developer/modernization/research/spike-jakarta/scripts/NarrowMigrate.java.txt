// Runs Apache Tomcat's jakartaee-migration tool with a custom NARROW profile: only the namespaces Tomcat
// itself provides (servlet incl. jsp, el, websocket, security.auth.message, and the Common Annotations
// classes in Tomcat's annotations-api.jar). Unlike the stock TOMCAT profile it leaves javax.persistence,
// javax.mail, javax.ejb and javax.transaction untouched, as the uPortal 6.0 research report proposes.
// usage: java -cp jakartaee-migration-1.0.13-shaded.jar NarrowMigrate.java <src> <dest> [exclude-glob...]
import java.io.File;
import java.util.regex.Pattern;
import org.apache.tomcat.jakartaee.EESpecProfile;
import org.apache.tomcat.jakartaee.Migration;

public class NarrowMigrate {
    static final Pattern NARROW = Pattern.compile("javax([/\\.](annotation[/\\.](Generated|ManagedBean|PostConstruct|PreDestroy|Priority"
            + "|Resource|Resources|security[/\\.]DeclareRoles|security[/\\.]DenyAll|security[/\\.]PermitAll|security[/\\.]RolesAllowed"
            + "|security[/\\.]RunAs|sql[/\\.]DataSourceDefinition)|el|servlet|websocket|security[/\\.]auth[/\\.]message))");

    public static void main(String[] a) throws Exception {
        Migration m = new Migration();
        m.setEESpecProfile(new EESpecProfile() {
            public String getSource() { return "javax"; }
            public String getTarget() { return "jakarta"; }
            public Pattern getPattern() { return NARROW; }
            public String toString() { return "NARROW(servlet,jsp,el,websocket,jaspic,annotations)"; }
        });
        m.setSource(new File(a[0]));
        m.setDestination(new File(a[1]));
        for (int i = 2; i < a.length; i++) m.addExclude(a[i]);
        m.execute();
    }
}
