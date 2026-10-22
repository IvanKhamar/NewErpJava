package ua.newerp.application.production;

public record ProductionModelFilter(String type, Boolean belowMinimumOnly) {

    public boolean onlyBelowMinimum() {
        return Boolean.TRUE.equals(belowMinimumOnly);
    }
}
