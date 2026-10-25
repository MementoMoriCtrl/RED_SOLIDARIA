package pe.redsolidaria.modules.donations.api.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class DonationCatalogDtos {
    private DonationCatalogDtos() {}
    public record CatalogItem(Long id, String nombre, String ubicacion) {}
    public record CatalogPage(List<CatalogItem> content, int page, int totalPages) {}
    public record CreateCatalogRequest(@NotBlank @Size(max = 150) String nombre,
            @Size(max = 200) String ubicacion, String descripcion) {}
}
