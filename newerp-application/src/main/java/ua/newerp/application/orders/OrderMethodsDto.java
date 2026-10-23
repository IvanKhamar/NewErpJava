package ua.newerp.application.orders;

import java.util.List;

public record OrderMethodsDto(List<OrderMethodDto> shipping, List<OrderMethodDto> payment) {
}
