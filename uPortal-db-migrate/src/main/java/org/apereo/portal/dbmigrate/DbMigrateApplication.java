package org.apereo.portal.dbmigrate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;

/**
 * The Spring Boot entry point of the executable jar ({@code
 * uPortal-db-migrate-<version>-exec.jar}):
 *
 * <pre>
 * java -Dloader.path=postgresql.jar,mariadb-java-client.jar -jar uPortal-db-migrate-exec.jar \
 *     --source-properties source.properties --target-properties target.properties --report r.json
 * </pre>
 *
 * There is no auto-configuration: the data sources, transaction managers and the Spring Batch
 * infrastructure are built from the command line by {@link DbMigrate}. The exit status is the
 * tool's ({@link DbMigrateCli}).
 */
@SpringBootConfiguration
public class DbMigrateApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DbMigrateApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Bean
    Runner runner() {
        return new Runner();
    }

    /** Runs the tool with the raw command line and keeps its exit status for Spring Boot. */
    static final class Runner implements ApplicationRunner, ExitCodeGenerator {
        private int status;

        @Override
        public void run(ApplicationArguments args) {
            status = DbMigrateCli.run(args.getSourceArgs(), System.out, System.err);
        }

        @Override
        public int getExitCode() {
            return status;
        }
    }
}
