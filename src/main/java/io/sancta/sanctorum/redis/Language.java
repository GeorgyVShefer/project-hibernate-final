package io.sancta.sanctorum.redis;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Language {
    private String language;
    private Boolean isOfficial;
    private BigDecimal percentage;
}
