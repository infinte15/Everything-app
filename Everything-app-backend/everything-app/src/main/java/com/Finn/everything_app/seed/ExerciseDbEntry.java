package com.Finn.everything_app.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ein Datensatz aus {@code data/exercisedb.json}.
 *
 * <p>Erzeugt von {@code tools/build-exercisedb.py} aus
 * <a href="https://github.com/hasaneyldrm/exercises-dataset">hasaneyldrm/exercises-dataset</a>.
 * Die Metadaten stehen unter der MIT-Lizenz und liegen deshalb im Repository. Die Medien,
 * auf die {@link #image()} und {@link #gifUrl()} zeigen, sind (c) Gym visual und liegen
 * bewusst nicht hier - der Seeder setzt nur URLs.
 *
 * <p>Beispiel:
 * <pre>
 * {"id":"0001","name":"3/4 sit-up","body_part":"waist","equipment":"body weight",
 *  "target":"abs","secondary_muscles":["hip flexors","lower back"],
 *  "media_id":"2gPfomN","image":"images/0001-2gPfomN.jpg",
 *  "gif_url":"videos/0001-2gPfomN.gif","instructions":["Lie flat on your back ..."]}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseDbEntry(
        String id,
        String name,
        @JsonProperty("body_part") String bodyPart,
        String equipment,
        String target,
        @JsonProperty("secondary_muscles") List<String> secondaryMuscles,
        @JsonProperty("media_id") String mediaId,
        String image,
        @JsonProperty("gif_url") String gifUrl,
        List<String> instructions
) {
}
