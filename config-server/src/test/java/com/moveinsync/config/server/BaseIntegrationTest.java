package com.moveinsync.config.server;

import com.moveinsync.config.server.security.JwtService;
import com.moveinsync.config.server.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.net.Socket;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static {
        ensurePostgresRunning();
    }

    private static void ensurePostgresRunning() {
        try (Socket s = new Socket("127.0.0.1", 5432)) {
            // Already running
            return;
        } catch (Exception ignored) {
            // Not running, start it
        }

        try {
            File pgDir = new File("/tmp/pgdata");
            if (!new File(pgDir, "PG_VERSION").exists()) {
                pgDir.mkdirs();
                new ProcessBuilder("/usr/lib/postgresql/15/bin/initdb", "-D", "/tmp/pgdata", "-U", "postgres", "-A", "trust")
                        .inheritIO().start().waitFor();
            }

            new ProcessBuilder("/usr/lib/postgresql/15/bin/pg_ctl", "-D", "/tmp/pgdata", "-l", "/tmp/pgdata/logfile",
                    "-o", "-k /tmp -p 5432 -h 127.0.0.1", "start")
                    .inheritIO().start().waitFor();

            // Wait until accepting connections
            for (int i = 0; i < 20; i++) {
                try (Socket s = new Socket("127.0.0.1", 5432)) {
                    break;
                } catch (Exception e) {
                    Thread.sleep(200);
                }
            }

            // Create configdb if needed
            new ProcessBuilder("/usr/lib/postgresql/15/bin/psql", "-h", "127.0.0.1", "-p", "5432", "-U", "postgres",
                    "-c", "CREATE DATABASE configdb;")
                    .inheritIO().start().waitFor();

        } catch (Exception e) {
            System.err.println("Could not auto-start postgres: " + e.getMessage());
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JwtService jwtService;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpHeaders authHeaders(String subject, Set<Role> roles, Set<String> tenants) {
        String token = jwtService.mint(subject, roles, tenants);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders adminHeaders() {
        return authHeaders("admin-test", Set.of(Role.ADMIN, Role.OPERATOR), Set.of("*"));
    }
}

