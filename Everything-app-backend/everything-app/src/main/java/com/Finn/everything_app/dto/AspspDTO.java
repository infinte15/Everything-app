package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ein Institut in der Bankenauswahl.
 *
 * <p>Ohne ID: Enable Banking identifiziert ein Institut ueber Name und Land. Beides muss die App
 * unveraendert zurueckschicken.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AspspDTO {

    private String name;
    private String country;
    private String logoUrl;

    /** Verbund ("Volksbanken Raiffeisenbanken") - die einzige Moeglichkeit, die Liste zu buendeln. */
    private String group;

    private boolean beta;

    /** {@code false} heisst: die Bank kann den Browser-Ablauf nicht und wird nicht angeboten. */
    private boolean redirectSupported;
}
