package au.org.aodn.oceancurrent.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WaveTagId implements Serializable {

    private String tagfile;
    private Integer order;
}
