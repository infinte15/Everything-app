package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Ein benanntes Set verfuegbarer Geraete. */
@Data
public class EquipmentProfileDTO {
    private Long id;

    @NotBlank(message = "Name erforderlich")
    @Size(max = 100, message = "Name darf maximal 100 Zeichen lang sein")
    private String name;

    private boolean isActive;

    /** Leer heisst "alles verfuegbar" - dann filtert das Profil nicht. */
    private List<String> equipment = new ArrayList<>();
}
