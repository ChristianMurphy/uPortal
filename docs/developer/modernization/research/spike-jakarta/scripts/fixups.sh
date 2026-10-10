#!/bin/bash
# Spike workarounds applied to a deployed (already transformed) webapps tree. Each one is a blocker
# workaround recorded in spike-jakarta.md; none modifies the transformed/ originals.
# usage: fixups.sh <catalina_base>
CB=${1:-spike/tomcat11}; X=spike/transformed/extra
cd $CB/webapps || exit 1
# F1: JSTL 1.1.2 (taglibs:standard:1.1.2) calls the JSP 2.0 jsp.el API removed in Pages 3.1 -> swap in
#     Apache Taglibs 1.2.5 impl+spec (migrated), as Tomcat's migration guide recommends. Same TLD URIs.
for w in */; do w=${w%/}
  if [ -f $w/WEB-INF/lib/standard-1.1.2.jar ]; then
    rm -f $w/WEB-INF/lib/standard-1.1.2.jar $w/WEB-INF/lib/jstl-1.1.2.jar $w/WEB-INF/lib/jstl-1.2.jar
    cp $X/taglibs-standard-impl-1.2.5.jar $X/taglibs-standard-spec-1.2.5.jar $w/WEB-INF/lib/
    # JSTL 1.0 "rt/el" URIs (java.sun.com/jstl/*) need the jstlel jar
    grep -rqs "java.sun.com/jstl/" --include=*.jsp --include=*.jspf $w && cp $X/taglibs-standard-jstlel-1.2.5.jar $w/WEB-INF/lib/
    echo "F1 JSTL swap: $w"
  fi
done
# F2: Spring Boot 1.5 (NotificationPortlet) introspects TomcatEmbeddedServletContainerFactory, which needs
#     org.apache.coyote.http11.AbstractHttp11JsseProtocol (removed in Tomcat 10.1). Exclude the auto-config.
P=NotificationPortlet/WEB-INF/classes/application.properties
if [ -f $P ] && ! grep -q "EmbeddedServletContainerAutoConfiguration" $P; then
  printf '\n# spike F2\nspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration\n' >> $P; echo "F2 boot exclude: $P"
fi
# F3: Spring 3.1/3.2 (CAS 3.6, FeedbackPortlet) ExpressionEvaluationUtils and JSTL 1.1 reference the JSP 2.0
#     jakarta.servlet.jsp.el.* classes removed in Pages 3.1; class verification fails even though the code path
#     is inactive. Shim: the six classes from Tomcat 8.5's jsp-api.jar, migrated, in shared/lib (webapp loaders
#     delegate jakarta.servlet.* to their parents, so the shared loader supplies them).
cp spike/transformed/extra/jsp-el-removed-shim.jar $CB/shared/lib/ && echo "F3 jsp.el shim -> $CB/shared/lib"
