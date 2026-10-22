package ua.newerp.application.production;

import java.util.List;

public record SetComponentBomRequest(List<ComponentBomRowRequest> rows) {

    public SetComponentBomRequest {
        rows = rows == null ? List.of() : rows;
    }
}
