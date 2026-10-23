package ua.newerp.application.returns;

/** A new return status with a comment for the log and, if needed, a new action. */
public record AddReturnHistoryRequest(int statusId, String comment, Integer actionId) {
}
