package ua.newerp.application.production;

import java.util.List;

public record SetSewnBomRequest(List<SewnBomRowRequest> rows) {

    public SetSewnBomRequest {
        rows = rows == null ? List.of() : rows;
    }
}
