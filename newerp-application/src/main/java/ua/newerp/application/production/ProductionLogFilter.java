package ua.newerp.application.production;

/** stage: "cut" or "sewn". limit: 1..500, 100 by default. */
public record ProductionLogFilter(Integer productionId, String stage, Integer limit) {

    public ProductionLogFilter {
        limit = Math.clamp(limit == null ? 100 : limit, 1, 500);
    }
}
