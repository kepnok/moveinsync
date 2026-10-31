package com.moveinsync.demo.controller;

import com.moveinsync.config.core.model.ConfigContext;
import com.moveinsync.config.core.model.ExplainResult;
import com.moveinsync.config.core.model.ResolvedConfig;
import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.sdk.ConfigView;
import com.moveinsync.config.sdk.stats.ClientStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final ConfigClient configClient;

    public DemoController(ConfigClient configClient) {
        this.configClient = configClient;
    }

    public record CutoffResponse(
            String key,
            long value,
            String source,
            boolean fromDefault,
            long snapshotVersion
    ) {}

    @GetMapping("/cutoff")
    public ResponseEntity<?> getCutoff(
            @RequestParam(required = false, defaultValue = "booking.cutoff") String key,
            @RequestParam(required = false, defaultValue = "acme") String client,
            @RequestParam(required = false) String office,
            @RequestParam(required = false) String shift
    ) {
        ConfigContext ctx = ConfigContext.of(client, office, shift);
        ConfigView view = configClient.view(client);

        long cutoff = view.getLong(key, ctx);
        ExplainResult explain = view.explain(key, ctx);
        ResolvedConfig effective = explain.effective();

        return ResponseEntity.ok(new CutoffResponse(
                key,
                cutoff,
                effective.source().key(),
                effective.fromDefault(),
                effective.snapshotVersion()
        ));
    }

    @GetMapping("/explain")
    public ResponseEntity<ExplainResult> explain(
            @RequestParam(required = false, defaultValue = "booking.cutoff") String key,
            @RequestParam(required = false, defaultValue = "acme") String client,
            @RequestParam(required = false) String office,
            @RequestParam(required = false) String shift
    ) {
        ConfigContext ctx = ConfigContext.of(client, office, shift);
        ConfigView view = configClient.view(client);
        return ResponseEntity.ok(view.explain(key, ctx));
    }

    @GetMapping("/stats")
    public ResponseEntity<ClientStats> stats() {
        return ResponseEntity.ok(configClient.stats());
    }
}
