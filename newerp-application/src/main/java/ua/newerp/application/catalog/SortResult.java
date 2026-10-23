package ua.newerp.application.catalog;

/** @param snapshotId the snapshot of the order before the change (to roll back); null for the cross-sell panel, which has its own order */
public record SortResult(Integer snapshotId, int updated) {
}
