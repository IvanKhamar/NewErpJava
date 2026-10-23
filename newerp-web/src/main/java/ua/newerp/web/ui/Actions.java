package ua.newerp.web.ui;

import org.springframework.dao.OptimisticLockingFailureException;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;

/**
 * Runs a user action and shows the outcome. The same errors the API turns into ProblemDetails (ErrorHandler)
 * become a red message here; anything else is a bug and goes to Vaadin's error handling.
 */
public final class Actions {

    private Actions() {
    }

    /** @return whether the action went through */
    public static boolean run(String success, Runnable action) {
        try {
            action.run();
        } catch (NotFoundException | DomainException e) {
            Messages.error(e.getMessage());
            return false;
        } catch (OptimisticLockingFailureException e) {
            Messages.error("Дані щойно змінив хтось інший, повторіть дію.");
            return false;
        }
        if (success != null) {
            Messages.success(success);
        }
        return true;
    }
}
